package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;
import water.util.PrettyPrint;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Hashtable;

class SplitByMSBLocal extends MRTask<SplitByMSBLocal> {
  private final boolean _isLeft;
  private final int _shift, _batchSize, _bytesUsed[], _keySize;
  private final BigInteger _base[];
  private final int  _col[];
  private final Key _linkTwoMRTask;
  private final int _id_maps[][];
  private final int[] _ascending;

  private transient long _counts[][];
  private transient long _o[][][];  // transient ok because there is no reduce here between nodes, and important to save shipping back to caller.
  private transient byte _x[][][];
  private long _numRowsOnThisNode;

  static Hashtable<Key,SplitByMSBLocal> MOVESHASH = new Hashtable<>();
  SplitByMSBLocal(boolean isLeft, BigInteger base[], int shift, int keySize, int batchSize, int bytesUsed[], int[] col, Key linkTwoMRTask, int[][] id_maps, int[] ascending) {
    _isLeft = isLeft;
    // we only currently use the shift (in bits) for the first column for the
    // MSB (which we don't know from bytesUsed[0]). Otherwise we use the
    // bytesUsed to write the key's bytes.
    _shift = shift;
    _batchSize=batchSize; _bytesUsed=bytesUsed; _col=col; _base=base;
    _keySize = keySize;
    _linkTwoMRTask = linkTwoMRTask;
    _id_maps = id_maps;
    _ascending = ascending;
  }

  @Override protected void setupLocal() {

    Key k = RadixCount.getKey(_isLeft, _col[0], H2O.SELF);
    _counts = ((RadixCount.Long2DArray) DKV.getGet(k))._val;   // get the sparse spine for this node, created and DKV-put above
    DKV.remove(k);
    // First cumulate MSB count histograms across the chunks in this node
    long MSBhist[] = MemoryManager.malloc8(256);
    int nc = _fr.anyVec().nChunks();
    assert nc == _counts.length;
    for (int c = 0; c < nc; c++) {
      if (_counts[c]!=null) {
        for (int h = 0; h < 256; h++) {
          MSBhist[h] += _counts[c][h];
        }
      }
    }
    _numRowsOnThisNode = ArrayUtils.sum(MSBhist);   // we just use this count for the DKV data transfer rate message
    if (ArrayUtils.maxValue(MSBhist) > Math.max(1000, _fr.numRows() / 20 / H2O.CLOUD.size())) {  // TO DO: better test of a good even split
      Log.warn("RadixOrder(): load balancing on this node not optimal (max value should be <= "
              + (Math.max(1000, _fr.numRows() / 20 / H2O.CLOUD.size()))
              + " " + Arrays.toString(MSBhist) + ")");
    }
    // shared between threads on the same node, all mappers write into distinct
    // locations (no conflicts, no need to atomic updates, etc.)
    System.out.print("Allocating _o and _x buckets on this node with known size up front ... ");
    long t0 = System.nanoTime();
    _o = new long[256][][];
    _x = new byte[256][][];  // for each bucket, there might be > 2^31 bytes, so an extra dimension for that
    for (int msb = 0; msb < 256; msb++) {
      if (MSBhist[msb] == 0) continue;
      int nbatch = (int) ((MSBhist[msb]-1)/_batchSize +1);  // at least one batch
      int lastSize = (int) (MSBhist[msb] - (nbatch-1) * _batchSize);   // the size of the last batch (could be batchSize)
      assert nbatch > 0;
      assert lastSize > 0;
      _o[msb] = new long[nbatch][];
      _x[msb] = new byte[nbatch][];
      int b;
      for (b = 0; b < nbatch-1; b++) {
        _o[msb][b] = MemoryManager.malloc8(_batchSize);          // TODO?: use MemoryManager.malloc8()
        _x[msb][b] = MemoryManager.malloc1(_batchSize * _keySize);
      }
      _o[msb][b] = MemoryManager.malloc8(lastSize);
      _x[msb][b] = MemoryManager.malloc1(lastSize * _keySize);
    }
    System.out.println("done in " + (System.nanoTime() - t0) / 1e9);


    // TO DO: otherwise, expand width. Once too wide (and interestingly large
    // width may not be a problem since small buckets won't impact cache),
    // start rolling up bins (maybe into pairs or even quads)
    for (int msb = 0; msb < 256; msb++) {
      // each of the 256 columns starts at 0 for the 0th chunk. This 0 offsets
      // into x[MSBvalue][batch div][mod] and o[MSBvalue][batch div][mod]
      long rollSum = 0;
      for (int c = 0; c < nc; c++) {
        if (_counts[c] == null) continue;
        long tmp = _counts[c][msb];
        // Warning: modify the POJO DKV cache, but that's fine since this node
        // won't ask for the original DKV.get() version again
        _counts[c][msb] = rollSum;
        rollSum += tmp;
      }
    }

    MOVESHASH.put(_linkTwoMRTask, this);

    // NB: no radix skipping in this version (unlike data.table we'll use
    // biggestBit and assume further bits are used).
  }


  @Override public void map(Chunk chk[]) {
    long myCounts[] = _counts[chk[0].cidx()]; //cumulative offsets into o and x
    if (myCounts == null) {
      System.out.println("myCounts empty for chunk " + chk[0].cidx());
      return;
    }

    // Loop through this chunk and write the byte key and the source row number
    // into the local MSB buckets
    // TODO: make this branch free and write the already-compressed _mem
    // directly.  Just need to normalize compression across all chunks.  This
    // has to loop through rows because we need the MSBValue from the first
    // column to use on the others, by row.  Nothing to do cache efficiency,
    // although, it will be most cache efficient (holding one page of each
    // column's _mem, plus a page of this_x, all contiguous.  At the cost of
    // more instructions.
    boolean[] isIntCols = MemoryManager.mallocZ(chk.length);
    for (int c=0; c < chk.length; c++){
      isIntCols[c] = chk[c].vec().isCategorical() || chk[c].vec().isInt();
    }

    for (int r=0; r<chk[0]._len; r++) {    // tight, branch free and cache efficient (surprisingly)
      int MSBvalue = 0;  // default for NA
     //long thisx = 0;
      BigInteger thisx = BigInteger.ZERO;
      if (!chk[0].isNA(r)) {
        // TODO: restore branch-free again, go by column and retain original
        // compression with no .at8()
        if (_isLeft && _id_maps[0]!=null) { // dealing with enum columns
          thisx = BigInteger.valueOf(_id_maps[0][(int)chk[0].at8(r)] + 1);
          MSBvalue = thisx.shiftRight(_shift).intValue();
          // may not be worth that as has to be global minimum so will rarely be
          // able to use as raw, but when we can maybe can do in bulk
        } else {    // dealing with numeric columns (int or double) or enum
          thisx = isIntCols[0] ?
                  BigInteger.valueOf(_ascending[0]*chk[0].at8(r)).subtract(_base[0]).add(BigInteger.ONE):
                  MathUtils.convertDouble2BigInteger(_ascending[0]*chk[0].atd(r)).subtract(_base[0]).add(BigInteger.ONE);
          MSBvalue = thisx.shiftRight(_shift).intValue();
        }
      }

      long target = myCounts[MSBvalue]++;
      int batch = (int) (target / _batchSize);
      int offset = (int) (target % _batchSize);
      assert _o[MSBvalue] != null;
      _o[MSBvalue][batch][offset] = (long) r + chk[0].start();    // move i and the index.

      byte this_x[] = _x[MSBvalue][batch];
      offset *= _keySize; // can't overflow because batchsize was chosen above to be maxByteSize/max(keysize,8)

      byte keyArray[] = thisx.toByteArray();  // switched already here.
      int offIndex = keyArray.length > 8 ? -1 : _bytesUsed[0] - keyArray.length;
      int endLen = _bytesUsed[0] - (keyArray.length > 8 ? 8 : keyArray.length);

      for (int i = _bytesUsed[0] - 1; (i >= endLen && i >= 0); i--) {
        this_x[offset + i] = keyArray[i - offIndex];
      }
        // add on the key values with values from other columns
      for (int c=1; c<chk.length; c++) {  // TO DO: left align subsequent
        offset += _bytesUsed[c-1];     // advance offset by the previous field width
        if (chk[c].isNA(r)) continue;  // NA is a zero field so skip over as java initializes memory to 0 for us always
        if (_isLeft && _id_maps[c] != null) thisx = BigInteger.valueOf(_id_maps[c][(int)chk[c].at8(r)] + 1);
        else {
          thisx =  isIntCols[c]?
                  BigInteger.valueOf(_ascending[c]*chk[c].at8(r)).subtract(_base[c]).add(BigInteger.ONE):
                  MathUtils.convertDouble2BigInteger(_ascending[c]*chk[c].atd(r)).subtract(_base[c]).add(BigInteger.ONE);
        }
        keyArray = thisx.toByteArray();  // switched already here.
        offIndex = keyArray.length > 8 ? -1 : _bytesUsed[c] - keyArray.length;
        endLen = _bytesUsed[c] - (keyArray.length > 8 ? 8 : keyArray.length);
        for (int i = _bytesUsed[c] - 1; (i >= endLen && i >= 0); i--) {
          this_x[offset + i] = keyArray[i - offIndex];
        }
      }
    }
  }

  static H2ONode ownerOfMSB(int MSBvalue) {
    // TO DO: this isn't properly working for efficiency. This value should pick the value of where it is, somehow.
    //        Why not getSortedOXHeader(MSBvalue).home_node() ?
    //int blocksize = (int) Math.ceil(256. / H2O.CLOUD.size());
    //H2ONode node = H2O.CLOUD._memary[MSBvalue / blocksize];
    return H2O.CLOUD._memary[MSBvalue % H2O.CLOUD.size()];   // spread it around more.
  }

  static Key getNodeOXbatchKey(boolean isLeft, int MSBvalue, int node, int batch) {
    return Key.make("__radix_order__NodeOXbatch_MSB" + MSBvalue + "_node" + node + "_batch" + batch + (isLeft ? "_LEFT" : "_RIGHT"),
            (byte) 1, Key.HIDDEN_USER_KEY, false, SplitByMSBLocal.ownerOfMSB(MSBvalue));
  }

  static Key getSortedOXbatchKey(boolean isLeft, int MSBvalue, int batch) {
    return Key.make("__radix_order__SortedOXbatch_MSB" + MSBvalue + "_batch" + batch + (isLeft ? "_LEFT" : "_RIGHT"),
            (byte) 1, Key.HIDDEN_USER_KEY, false, SplitByMSBLocal.ownerOfMSB(MSBvalue));
  }


  static class OXbatch extends Iced {
    OXbatch(long[] o, byte[] x) { _o = o; _x = x; }
    final long[/*batchSize or lastSize*/] _o;
    final byte[/*batchSize or lastSize*/] _x;
  }

  static Key getMSBNodeHeaderKey(boolean isLeft, int MSBvalue, int node) {
    return Key.make("__radix_order__OXNodeHeader_MSB" + MSBvalue + "_node" + node + (isLeft ? "_LEFT" : "_RIGHT"),
            (byte) 1, Key.HIDDEN_USER_KEY, false, SplitByMSBLocal.ownerOfMSB(MSBvalue));
  }

  static class MSBNodeHeader extends Iced {
    MSBNodeHeader(int MSBnodeChunkCounts[/*chunks*/]) { _MSBnodeChunkCounts = MSBnodeChunkCounts;}
    int _MSBnodeChunkCounts[];   // a vector of the number of contributions from each chunk.  Since each chunk is length int, this must less than that, so int
  }

  // Push o/x in chunks to owning nodes
  void sendSplitMSB() {
    // The map() above ran above for each chunk on this node.  Although this
    // data was written to _o and _x in the order of chunk number (because we
    // calculated those offsets in order in the prior step), the chunk numbers
    // will likely have gaps because chunks are distributed across nodes not
    // using a modulo approach but something like chunk1 on node1, chunk2 on
    // node2, etc then modulo after that.  Also, as tables undergo changes as a
    // result of user action, their distribution of chunks to nodes could
    // change or be changed (e.g. 'Tomas' rebalance()') for various reasons.
    // When the helper node (i.e the node doing all the A's) gets the A's from
    // this node, it must stack all this nodes' A's with the A's from the other
    // nodes in chunk order in order to maintain the original order of the A's
    // within the global table.  To to do that, this node must tell the helper
    // node where the boundaries are in _o and _x.  That's what the first for
    // loop below does.  The helper node doesn't need to be sent the
    // corresponding chunk numbers. He already knows them from the Vec header
    // which he already has locally.

    // TODO: perhaps write to o_ and x_ in batches in the first place, and just
    // send more and smaller objects via the DKV.  This makes the stitching
    // much easier on the helper node too, as it doesn't need to worry about
    // batch boundaries in the source data.  Then it might be easier to
    // parallelize that helper part.  The thinking was that if each chunk
    // generated 256 objects, that would flood the DKV with keys?

    // TODO: send nChunks * 256.  Currently we do nNodes * 256.  Or avoid DKV
    // altogether if possible.

    System.out.print("Starting SendSplitMSB on this node (keySize is " + _keySize + " as [");
    for( int bs : _bytesUsed ) System.out.print(" "+bs);
    System.out.println(" ]) ...");

    long t0 = System.nanoTime();
    Futures myfs = new Futures(); // Private Futures instead of _fs, so can block early and get timing results
    for (int msb =0; msb <_o.length /*256*/; ++msb) {   // TODO this can be done in parallel, surely
      // "I found my A's (msb=0) and now I'll send them to the node doing all the A's"
      // "I'll send you a long vector of _o and _x (batched if very long) along with where the boundaries are."
      // "You don't need to know the chunk numbers of these boundaries, because you know the node of each chunk from your local Vec header"
      if(_o[msb] == null) continue;
      myfs.add(H2O.submitTask(new SendOne(msb,myfs)));
    }
    myfs.blockForPending();
    double timeTaken = (System.nanoTime() - t0) / 1e9;
    long bytes = _numRowsOnThisNode*( 8/*_o*/ + _keySize) + 64;
    System.out.println("took : " + timeTaken);
    System.out.println("  DKV.put " + PrettyPrint.bytes(bytes) + " @ " +
                       String.format("%.3f", bytes / timeTaken / (1024*1024*1024)) + " GByte/sec  [10Gbit = 1.25GByte/sec]");
  }

  class SendOne extends H2O.H2OCountedCompleter<SendOne> {
    // Nothing on remote node here, just a local parallel loop
    private final int _msb;
    private final Futures _myfs;
    SendOne(int msb, Futures myfs) { _msb = msb; _myfs = myfs; }

    @Override public void compute2() {
      int numChunks = 0;  // how many of the chunks are on this node
      for( long[] cnts : _counts )
        if (cnts != null)  // the map() allocated the 256 vector in the spine slots for this node's chunks
          // even if cnts[_msb]==0 (no _msb for this chunk) we'll store
          // that because needed by line marked LINE_ANCHOR_1 below.
          numChunks++;
      // make dense.  And by construction (i.e. cumulative counts) these chunks
      // contributed in order
      int msbNodeChunkCounts[] = MemoryManager.malloc4(numChunks);
      int j=0;
      long lastCount = 0; // _counts are cumulative at this stage so need to diff
      for( long[] cnts : _counts ) {
        if (cnts != null) {
          if (cnts[_msb] == 0) {  // robust in case we skipped zeros when accumulating
            msbNodeChunkCounts[j] = 0;
          } else {
            // _counts is long so it can be accumulated in-place iirc.
            // TODO: check
            msbNodeChunkCounts[j] = (int)(cnts[_msb] - lastCount);
            lastCount = cnts[_msb];
          }
          j++;
        }
      }
      MSBNodeHeader msbh = new MSBNodeHeader(msbNodeChunkCounts);
      // Need dontCache==true, so data does not remain both locally and on remote.
      // Use private Futures so can block independent of MRTask Futures.
      DKV.put(getMSBNodeHeaderKey(_isLeft, _msb, H2O.SELF.index()), msbh, _myfs, true);
      for (int b=0;b<_o[_msb].length; b++) {
        OXbatch ox = new OXbatch(_o[_msb][b], _x[_msb][b]);   // this does not copy in Java, just references
        DKV.put(getNodeOXbatchKey(_isLeft, _msb, H2O.SELF.index(), b), ox, _myfs, true);
      }
      tryComplete();
    }
  }
}
