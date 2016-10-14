package water.rapids;

import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;


// counted completer so that left and right index can run at the same time
class RadixOrder extends H2O.H2OCountedCompleter<RadixOrder> {
  private final Frame _DF;
  private final boolean _isLeft;
  private final int _whichCols[], _id_maps[][];
  final int _shift[];
  final int _bytesUsed[];
  final long _base[];

  RadixOrder(Frame DF, boolean isLeft, int whichCols[], int id_maps[][]) {
    _DF = DF;
    _isLeft = isLeft;
    _whichCols = whichCols;
    _id_maps = id_maps;
    _shift = new int[_whichCols.length];   // currently only _shift[0] is used
    _bytesUsed = new int[_whichCols.length];
    _base = new long[_whichCols.length];
  }

  @Override
  public void compute2() {
    long t0 = System.nanoTime(), t1;
    initBaseShift();

    // The MSB is stored (seemingly wastefully on first glance) because we need
    // it when aligning two keys in Merge()
    int keySize = ArrayUtils.sum(_bytesUsed);
    // 256MB is the DKV limit.  / 2 because we fit o and x together in one OXBatch.
    int batchSize = 256*1024*1024 / Math.max(keySize, 8) / 2 ;
    // The Math.max ensures that batches of o and x are aligned, even for wide
    // keys.  To save % and / in deep iteration; e.g. in insert().
    System.out.println("Time to use rollup stats to determine biggestBit: " + ((t1=System.nanoTime()) - t0) / 1e9); t0=t1;

    if( _whichCols.length > 0 )
      new RadixCount(_isLeft, _base[0], _shift[0], _whichCols[0], _isLeft ? _id_maps : null ).doAll(_DF.vec(_whichCols[0]));
    System.out.println("Time of MSB count MRTask left local on each node (no reduce): " + ((t1=System.nanoTime()) - t0) / 1e9); t0=t1;

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
      new SplitByMSBLocal(_isLeft, _base, _shift[0], keySize, batchSize, _bytesUsed, _whichCols, linkTwoMRTask, _id_maps).doAll(_DF.vecs(_whichCols)); // postLocal needs DKV.put()
    System.out.println("SplitByMSBLocal MRTask (all local per node, no network) took : " + ((t1=System.nanoTime()) - t0) / 1e9); t0=t1;

    if( _whichCols.length > 0 )
      new SendSplitMSB(linkTwoMRTask).doAllNodes();
    System.out.println("SendSplitMSB across all nodes took : " + ((t1=System.nanoTime()) - t0) / 1e9); t0=t1;

    // dispatch in parallel
    RPC[] radixOrders = new RPC[256];
    System.out.print("Sending SingleThreadRadixOrder async RPC calls ... ");
    for (int i = 0; i < 256; i++)
      radixOrders[i] = new RPC<>(SplitByMSBLocal.ownerOfMSB(i), new SingleThreadRadixOrder(_DF, _isLeft, batchSize, keySize, /*nGroup,*/ i)).call();
    System.out.println("took : " + ((t1=System.nanoTime()) - t0) / 1e9); t0=t1;

    System.out.print("Waiting for RPC SingleThreadRadixOrder to finish ... ");
    for( RPC rpc : radixOrders )
      rpc.get();
    System.out.println("took " + (System.nanoTime() - t0) / 1e9);

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
      long max;
      if (col.isCategorical()) {
        // simpler and more robust for now for all categorical bases to be 0,
        // even though some subsets may be far above 0; i.e. forgo uncommon
        // efficiency savings for now
        _base[i] = 0;  
        if (_isLeft) {
          // the left's levels have been matched to the right's levels and we
          // store the mapped values so it's that mapped range we need here (or
          // the col.max() of the corresponding right table would be fine too,
          // but mapped range might be less so use that for possible
          // efficiency)
          assert _id_maps[i] != null;

          // TODO: what is in _id_maps for no matches (-1?) and exclude those
          // i.e. find the minimum >=0. Then treat -1 in _id_map as an NA when
          // writing key
          //_colMin[i] = ArrayUtils.minValue(_id_maps[i]);  
          // if we join to a small subset of levels starting at 0, we'll
          // benefit from the smaller range here, though
          max = ArrayUtils.maxValue(_id_maps[i]); 
        } else {
          max = (long)col.max();
        }
      } else {
        _base[i] = (long)col.min();
        max = (long)col.max();
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

      _bytesUsed[i] = (_shift[i]+15) / 8;
      //assert (biggestBit-1)/8 + 1 == _bytesUsed[i];
    }
  }

  // Compute the span or range between min and max.  Compute a
  // shift amount to bring the high order bits of the range down
  // low for radix sorting.  Lower the lower-bound to be an even
  // power of the shift.
  private long computeShift( final long max, final int i )  {
    long range = max - _base[i] + 2; // +1 for when min==max to include the bound, +1 for the leading NA spot
    // number of bits starting from 1 easier to think about (for me)
    int biggestBit = 1 + (int) Math.floor(Math.log(range) / Math.log(2));  
    // TODO: feed back to R warnings()
    if (biggestBit < 8) Log.warn("biggest bit should be >= 8 otherwise need to dip into next column (TODO)");  
    assert biggestBit >= 1;
    _shift[i] = Math.max(8, biggestBit)-8;
    long MSBwidth = 1L<<_shift[i];
    if (_base[i] % MSBwidth != 0) {
      // choose base lower than minimum so as to align boundaries (unless
      // minimum already on a boundary by chance)
      _base[i] = MSBwidth * (_base[i]/MSBwidth + (_base[i]<0 ? -1 : 0));
      assert _base[i] % MSBwidth == 0;
    }
    return (max - _base[i] + 1L) >> _shift[i];  // relied on in RadixCount.map
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

