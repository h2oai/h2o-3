package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.math.BigInteger;

import static java.math.BigInteger.ONE;
import static java.math.BigInteger.ZERO;
import static water.rapids.Merge.MEM_MULTIPLIER;
import static water.rapids.Merge.OPTIMAL_BATCHSIZE;


// counted completer so that left and right index can run at the same time
class RadixOrder extends H2O.H2OCountedCompleter<RadixOrder> {
  private final Frame _DF;
  private final boolean _isLeft;
  private final int _whichCols[], _id_maps[][];
  final boolean _isInt[];
  final boolean _isCategorical[];
  final int _shift[];
  final int _bytesUsed[];
  final BigInteger _base[];
  final int[] _ascending;  // 0 to sort ASC, 1 to sort DESC
  final long _mergeId;

  RadixOrder(Frame DF, boolean isLeft, int whichCols[], int id_maps[][], int[] ascending, long mergeId) {
    _DF = DF;
    _isLeft = isLeft;
    _whichCols = whichCols;
    _id_maps = id_maps;
    _shift = new int[_whichCols.length];   // currently only _shift[0] is used
    _bytesUsed = new int[_whichCols.length];
    //_base = new long[_whichCols.length];
    _base = new BigInteger[_whichCols.length];
    _isInt = new boolean[_whichCols.length];
    _isCategorical = new boolean[_whichCols.length];
    _ascending = ascending;
    _mergeId =  mergeId;
  }

  @Override
  public void compute2() {
    long t0 = System.nanoTime(), t1;
    initBaseShift();

    // The MSB is stored (seemingly wastefully on first glance) because we need
    // it when aligning two keys in Merge()
    int keySize = ArrayUtils.sum(_bytesUsed);
    // 256MB is the DKV limit.  / 2 because we fit o and x together in one OXBatch.
    int batchSize = OPTIMAL_BATCHSIZE ; // larger, requires more memory with less remote row fetch and vice versa for smaller
    // go through all node memory and reduce batchSize if needed
    long minMem = Long.MAX_VALUE; // memory size of nodes with smallest memory
    for (H2ONode h2o : H2O.CLOUD._memary) {
      long mem = h2o._heartbeat.get_free_mem(); // in bytes
      if (mem < minMem)
        minMem = mem;
    }
    // at some point, a MSB worth of compare columns will be stored at any one node.  Make sure we have enough memory
    // for that.
    long minSortMemory = _whichCols.length*Math.max(_DF.numRows(), batchSize)*8*MEM_MULTIPLIER;
    if (minMem < minSortMemory) // if not enough, just throw an error and get out
      throw new RuntimeException("The minimum memory per node needed is too small to accommodate the sorting/merging " +
              "operation.  Make sure the smallest node has at least "+minSortMemory+" bytes of memory.");
    // an array of size batchsize by numCols will be created for each sorted chunk in the end.  Memory is in bytes
    long dataSetMemoryPerRow = 8*((long) _DF.numCols())*MEM_MULTIPLIER; // 8 to translate 64 bits into 8 bytes, MEM_MULTIPLIER to scale up
    long batchMemory = Math.max((long) batchSize*dataSetMemoryPerRow, minSortMemory); // memory needed to store one chunk of dataset frame
    if (batchMemory > minMem) {  // batchsize is too big for node with smallest memory, reduce it
      batchSize = (int) Math.floor(minMem/dataSetMemoryPerRow);
      if (batchSize == 0)
        throw new RuntimeException("The minimum memory per node needed is too small to accommodate the sorting/merging " +
                "operation.  Make sure the smallest node has at least "+minMem*100+" bytes of memory.");
    }
    
    // The Math.max ensures that batches of o and x are aligned, even for wide
    // keys.  To save % and / in deep iteration; e.g. in insert().
    Log.debug("Time to use rollup stats to determine biggestBit: " + ((t1=System.nanoTime()) - t0) / 1e9+" seconds."); t0=t1;

    if( _whichCols.length > 0 ) // batchsize is not used here
      new RadixCount(_isLeft, _base[0], _shift[0], _whichCols[0], _id_maps, _ascending[0], _mergeId).doAll(_DF.vec(_whichCols[0]));
    Log.debug("Time of MSB count MRTask left local on each node (no reduce): " + ((t1=System.nanoTime()) - t0) / 1e9+" seconds."); t0=t1;

    // NOT TO DO:  we do need the full allocation of x[] and o[].  We need o[] anyway.  x[] will be compressed and dense.
    // o is the full ordering vector of the right size
    // x is the byte key aligned with o
    // o AND x are what bmerge() needs. Pushing x to each node as well as o avoids inter-node comms.

    // Workaround for incorrectly blocking closeLocal() in MRTask is to do a
    // double MRTask and pass a key between them to pass output from first on
    // that node to second on that node.
    // TODO: fix closeLocal() blocking issue and revert to simpler usage of closeLocal()
    Key linkTwoMRTask = Key.make();
    if( _whichCols.length > 0 )
      new SplitByMSBLocal(_isLeft, _base, _shift[0], keySize, batchSize, _bytesUsed, _whichCols, linkTwoMRTask, 
              _id_maps, _ascending, _mergeId).doAll(_DF.vecs(_whichCols)); // postLocal needs DKV.put()
    Log.debug("SplitByMSBLocal MRTask (all local per node, no network) took : " + ((t1=System.nanoTime()) - t0) / 1e9+" seconds."); t0=t1;

    if( _whichCols.length > 0 )
      new SendSplitMSB(linkTwoMRTask).doAllNodes();
    Log.debug("SendSplitMSB across all nodes took : " + ((t1=System.nanoTime()) - t0) / 1e9+" seconds."); t0=t1;

    // dispatch in parallel
    RPC[] radixOrders = new RPC[256];
    Log.info("Sending SingleThreadRadixOrder async RPC calls ... ");
    for (int i = 0; i < 256; i++)
      radixOrders[i] = new RPC<>(SplitByMSBLocal.ownerOfMSB(i), new SingleThreadRadixOrder(_DF, _isLeft, batchSize,
              keySize, /*nGroup,*/ i, _mergeId)).call();
    Log.debug("took : " + ((t1=System.nanoTime()) - t0) / 1e9); t0=t1;

    Log.info("Waiting for RPC SingleThreadRadixOrder to finish ... ");
    for( RPC rpc : radixOrders )
      rpc.get();
    Log.debug("took " + (System.nanoTime() - t0) / 1e9+" seconds.");

    tryComplete();

    // serial, do one at a time
//    for (int i = 0; i < 256; i++) {
//      H2ONode node = MoveByFirstByte.ownerOfMSB(i);
//      SingleThreadRadixOrder radixOrder = new RPC<>(node, new SingleThreadRadixOrder(DF, batchSize, keySize, nGroup, i)).call().get();
//      _o[i] = radixOrder._o;
//      _x[i] = radixOrder._x;
//    }

    // If sum(nGroup) == nrow then the index is unique.
    // 1) useful to know if an index is unique or not (when joining to it we
    //    know multiples can't be returned so can allocate more efficiently)
    // 2) If all groups are size 1 there's no need to actually allocate an
    //    all-1 group size vector (perhaps user was checking for uniqueness by
    //    counting group sizes)
    // 3) some nodes may have unique input and others may contain dups; e.g.,
    //    in the case of looking for rare dups.  So only a few threads may have
    //    found dups.
    // 4) can sweep again in parallel and cache-efficient finding the groups,
    //    and allocate known size up front to hold the group sizes.
    // 5) can return to Flow early with the group count. User may now realise
    //    they selected wrong columns and cancel early.
  }

  private void initBaseShift() {
    for (int i=0; i<_whichCols.length; i++) {
      Vec col = _DF.vec(_whichCols[i]);
      // TODO: strings that aren't already categoricals and fixed precision double.
      BigInteger max=ZERO;

      _isInt[i] = col.isCategorical() || col.isInt();
      _isCategorical[i] = col.isCategorical();
      if (col.isCategorical()) {
        // simpler and more robust for now for all categorical bases to be 0,
        // even though some subsets may be far above 0; i.e. forgo uncommon
        // efficiency savings for now
        _base[i] = ZERO;
        assert _id_maps[i] != null;
        max = _isLeft?BigInteger.valueOf(ArrayUtils.maxValue(_id_maps[i])):BigInteger.valueOf(col.domain().length);
      } else {
        double colMin = col.min();
        double colMax = col.max();
        if (col.isInt()) {
          GetLongStatsTask glst = GetLongStatsTask.getLongStats(col);
          long colMini = glst._colMin;
          long colMaxi = glst._colMax;

          _base[i] = BigInteger.valueOf(Math.min(colMini, colMaxi*(_ascending[i])));
          max = BigInteger.valueOf(Math.max(colMaxi, colMini*(_ascending[i])));
        } else{
          _base[i] = MathUtils.convertDouble2BigInteger(Math.min(col.min(), colMax*(_ascending[i])));
          max = MathUtils.convertDouble2BigInteger(Math.max(col.max(), colMin*(_ascending[i])));
        }
      }

      // Compute the span or range between min and max.  Compute a
      // shift amount to bring the high order bits of the range down
      // low for radix sorting.  Lower the lower-bound to be an even
      // power of the shift.
      long chk = computeShift(max, i);
      // On rare occasions, lowering the lower-bound also increases
      // the span or range until another bit is needed in the sort.
      // In this case, we need to re-compute the shift amount and
      // perhaps use an even lower lower-bound.
      if( chk == 256 ) chk = computeShift(max, i);
      assert chk <= 255;
      assert chk >= 0;

      _bytesUsed[i] = Math.min(8, (_shift[i]+15) / 8);  // should not go over 8 bytes
      //assert (biggestBit-1)/8 + 1 == _bytesUsed[i];
    }
  }

  // TODO: push these into Rollups?
  private static class GetLongStatsTask extends MRTask<GetLongStatsTask> {
    long _colMin=Long.MAX_VALUE;
    long _colMax=Long.MIN_VALUE;
    static GetLongStatsTask getLongStats(Vec col) {
      return new GetLongStatsTask().doAll(col);
    }
    @Override public void map(Chunk c) {
      for(int i=0; i<c._len; ++i) {
        if( !c.isNA(i) ) {
          long l = c.at8(i);
          _colMin = Math.min(_colMin, l);
          _colMax = Math.max(_colMax, l);
        }
      }
    }
    @Override public void reduce(GetLongStatsTask that) {
      _colMin = Math.min(_colMin, that._colMin);
      _colMax = Math.max(_colMax, that._colMax);
    }
  }

  // Compute the span or range between min and max.  Compute a
  // shift amount to bring the high order bits of the range down
  // low for radix sorting.  Lower the lower-bound to be an even
  // power of the shift.
  private long computeShift( final BigInteger max, final int i )  {
    int biggestBit = 0;

    int rangeD = max.subtract(_base[i]).add(ONE).add(ONE).bitLength();
    biggestBit = _isInt[i] ? rangeD : (rangeD == 64 ? 64 : rangeD + 1);

    // TODO: feed back to R warnings()
    if (biggestBit < 8) Log.warn("biggest bit should be >= 8 otherwise need to dip into next column (TODO)");  
    assert biggestBit >= 1;
    _shift[i] = Math.max(8, biggestBit)-8;
    long MSBwidth = 1L << _shift[i];

    BigInteger msbWidth = BigInteger.valueOf(MSBwidth);
    if (_base[i].mod(msbWidth).compareTo(ZERO) != 0) {
      _base[i] =  _isInt[i]? msbWidth.multiply(_base[i].divide(msbWidth).add(_base[i].signum()<0?BigInteger.valueOf(-1L):ZERO))
              :msbWidth.multiply (_base[i].divide(msbWidth));; // dealing with unsigned integer here
      assert _base[i].mod(msbWidth).compareTo(ZERO) == 0;
    }
    return max.subtract(_base[i]).add(ONE).shiftRight(_shift[i]).intValue();
  }

  private static class SendSplitMSB extends MRTask<SendSplitMSB> {
    final Key _linkTwoMRTask;
    SendSplitMSB(Key linkTwoMRTask) { _linkTwoMRTask = linkTwoMRTask; }
    @Override public void setupLocal() {
      SplitByMSBLocal.MOVESHASH.get(_linkTwoMRTask).sendSplitMSB();
      SplitByMSBLocal.MOVESHASH.remove(_linkTwoMRTask);
    }
  }
}

