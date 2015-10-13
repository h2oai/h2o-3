package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.Log;

import java.util.Arrays;

class RadixCount extends MRTask<RadixCount> {
  public static class Long2DArray extends Iced {
    Long2DArray(int len) { _val = new long[len][]; }
    long _val[][];
  }
  Long2DArray _counts;
  int _biggestBit;
  int _col;
  Key _frameKey;

  RadixCount(Key frameKey, int biggestBit, int col) {
    _frameKey = frameKey;
    _biggestBit = biggestBit;
    _col = col;
  }

  // make a unique deterministic key as a function of frame, column and node
  // make it homed to the owning node
  static Key getKey(Key frKey, int col, H2ONode node) {
    return Key.make("counts_" + frKey.toString() + "_col" + col + "_node" + node, (byte) 1 /*replica factor*/, (byte) 31 /*hidden user-key*/, true, node);
  }

  @Override protected void setupLocal() {
    _counts = new Long2DArray(_fr.anyVec().nChunks());
  }

  @Override public void map( Chunk chk ) {
    long tmp[] = _counts._val[chk.cidx()] = new long[256];
    int shift = _biggestBit-8;
    if (shift<0) shift = 0;
    // TO DO: assert chk instanceof integer or enum;  -- but how?  // alternatively: chk.getClass().equals(C8Chunk.class)
    for (int r=0; r<chk._len; r++) {
      tmp[(int) (chk.at8(r) >> shift & 0xFFL)]++;  // forget the L => wrong answer with no type warning from IntelliJ
      // TO DO - use _mem directly. Hist the compressed bytes and then shift the histogram afterwards when reducing.
    }
  }

  @Override protected void postLocal() {
    DKV.put(getKey(_frameKey, _col, H2O.SELF), _counts);
  }
}

class MoveByFirstByte extends MRTask<MoveByFirstByte> {
  long _counts[][];
  long _MSBhist[];
  long _o[][][];
  byte _x[][][];
  Key _frameKey;
  int _biggestBit, _batchSize, _bytesUsed[], _keySize;
  int[]_col;
  MoveByFirstByte(Key frameKey, int biggestBit, int keySize, int batchSize, int bytesUsed[], int[] col) {
    _frameKey = frameKey;
    _biggestBit = biggestBit; _batchSize=batchSize; _bytesUsed = bytesUsed; _col = col;
    _keySize = keySize;  // ArrayUtils.sum(_bytesUsed) -1;
  }

  @Override protected void setupLocal() {
    // First accumulate counts across chunks in this node into histograms for most significant 8 bits
    _counts = ((RadixCount.Long2DArray)DKV.getGet(RadixCount.getKey(_frameKey, _col[0], H2O.SELF)))._val;
    _MSBhist = new long[256];  // total across nodes but retain original spine as we need that below
    int nc = _fr.anyVec().nChunks();
    for (int c = 0; c < nc; c++) {
      if (_counts[c]!=null) {
        for (int h = 0; h < 256; h++) {
          _MSBhist[h] += _counts[c][h];
        }
      }
    }
    if (ArrayUtils.maxValue(_MSBhist) > Math.max(1000, _fr.numRows() / 20 / H2O.CLOUD.size())) {  // TO DO: better test of a good even split
      Log.warn("RadixOrder(): load balancing on this node not optimal (max value should be <= "
              + (Math.max(1000, _fr.numRows() / 20 / H2O.CLOUD.size()))
              + " " + Arrays.toString(_MSBhist) + ")");
    }
    // shared between threads on the same node, all mappers write into distinct locations (no conflicts, no need to atomic updates, etc.)
    _o = new long[256][][];
    _x = new byte[256][][];  // for each bucket, there might be > 2^31 bytes, so an extra dimension for that
    for (int c = 0; c < 256; c++) {
      if (_MSBhist[c] == 0) continue;
      int nbatch = (int) (_MSBhist[c]-1)/_batchSize +1;  // at least one batch
      int lastSize = (int) (_MSBhist[c] - (nbatch-1) * _batchSize);   // the size of the last batch (could be batchSize)
      assert nbatch == 1;  // Prevent large testing for now.  TO DO: test nbatch>0 by reducing batchSize very small and comparing results with non-batched
      assert lastSize > 0;
      _o[c] = new long[nbatch][];
      _x[c] = new byte[nbatch][];
      int b;
      for (b = 0; b < nbatch-1; b++) {
        _o[c][b] = new long[_batchSize];          // TO DO?: use MemoryManager.malloc8()
        _x[c][b] = new byte[_batchSize * _keySize];
      }
      _o[c][b] = new long[lastSize];
      _x[c][b] = new byte[lastSize * _keySize];
    }

    // TO DO: otherwise, expand width. Once too wide (and interestingly large width may not be a problem since small buckets won't impact cache),
    // start rolling up bins (maybe into pairs or even quads)

    for (int h = 0; h < 256; h++) {
      long rollSum = 0;  // each of the 256 columns starts at 0 for the 0th chunk. This 0 offsets into x[MSBvalue][batch div][mod] and o[MSBvalue][batch div][mod]
      for (int c = 0; c < nc; c++) {
        long tmp = _counts[c][h];
        _counts[c][h] = rollSum; //Warning: modify the POJO DKV cache, but that's fine since this node won't ask for the original DKV.get() version again
        rollSum += tmp;
      }
    }
    // NB: no radix skipping in this version (unlike data.table we'll use biggestBit and assume further bits are used).
  }

  @Override public void map(Chunk chk[]) {
    long myCounts[] = _counts[chk[0].cidx()]; //cumulative offsets into o and x

    //int leftAlign = (8-(_biggestBit % 8)) % 8;   // only the first column is left aligned, currently. But they all could be for better splitting.
    for (int r=0; r<chk[0]._len; r++) {    // tight, branch free and cache efficient (surprisingly)
      long thisx = chk[0].at8(r);  // - _colMin[0]) << leftAlign;  (don't subtract colMin because it unlikely helps compression and makes joining 2 compressed keys more difficult and expensive).
      assert(_biggestBit >= 8) : "biggest bit should be >= 8, need to dip into next column otherwise";
      int MSBvalue = (int) (thisx >> (_biggestBit-8) & 0xFFL);
      long target = myCounts[MSBvalue]++;
      int batch = (int) (target / _batchSize);
      int offset = (int) (target % _batchSize);
      _o[MSBvalue][batch][offset] = (long) r + chk[0].start();    // move i and the index.

      byte this_x[] = _x[MSBvalue][batch];
      offset *= _keySize; //can't overflow because batchsize was chosen above to be maxByteSize/max(keysize,8)
      for (int i = _bytesUsed[0] - 1; i >= 0; i--) {   // a loop because I don't believe System.arraycopy() can copy parts of (byte[])long to byte[]
        this_x[offset + i] = (byte) (thisx & 0xFF);
        thisx >>= 8;
      }
      for (int c=1; c<chk.length; c++) {  // TO DO: left align subsequent
        offset += _bytesUsed[c-1]-1;  // advance offset by the previous field width
        thisx = chk[c].at8(r);        // TO DO : compress with a scale factor such as dates stored as ms since epoch / 3600000L
        for (int i = _bytesUsed[c] - 1; i >= 0; i--) {
          this_x[offset + i] = (byte) (thisx & 0xFF);
          thisx >>= 8;
        }
      }
    }
  }

  static H2ONode ownerOfMSB(int MSBvalue) {
    int blocksize = (int) Math.ceil(256. / H2O.CLOUD.size());
    H2ONode node = H2O.CLOUD._memary[MSBvalue / blocksize];
    return node;
  }

  static Key getIndexKeyForMSB(int MSBvalue, int batch, int fromNode) {
    return Key.make("ox_MSB_" + MSBvalue + "_batch_" + batch + "_fromNode_" + fromNode, (byte) 1 /*replica factor*/, (byte) 31 /*hidden user-key*/, true, ownerOfMSB(MSBvalue));
  }

  static class OXWrapper extends Iced {
    OXWrapper(long[] o, byte[] x) { _o = o; _x = x; }
    long[/*offset*/] _o;
    byte[/*offset*/] _x;
  }

  static Key getMSBHeaderKey(int MSBvalue, int fromNode) {
    return Key.make("header_MSB_" + MSBvalue + "_fromNode_" + fromNode, (byte) 1 /*replica factor*/, (byte) 31 /*hidden user-key*/, true, ownerOfMSB(MSBvalue));
  }

  static class MSBHeader extends Iced {
    MSBHeader(int MSBnodeChunkCounts[/*chunks*/]) { _MSBnodeChunkCounts = MSBnodeChunkCounts;}
    int _MSBnodeChunkCounts[];
  }

  // Push o/x in chunks to owning nodes
  @Override protected void postLocal() {
    int nc = _fr.anyVec().nChunks();
    for (int msb =0; msb <_o.length /*256*/; ++msb) {
      if(_o[msb] == null) continue;
      int numChunks = 0;  // how many of the chunks on this node had some rows with this MSB
      for (int c=0; c<nc; c++) if (_counts[c][msb] > 0) numChunks++;
      int MSBnodeChunkCounts[] = new int[numChunks];   // make dense.  And by construction (i.e. cumulative counts) these chunks contributed in order
      int j=0;
      for (int c=0; c<nc; c++) if (_counts[c][msb] > 0) MSBnodeChunkCounts[j++] = (int)_counts[c][msb];  // _counts is long so it can be accumulated in-place I think.  TO DO: check
      assert _MSBhist[msb] == ArrayUtils.sum(MSBnodeChunkCounts);
      MSBHeader msbh = new MSBHeader(MSBnodeChunkCounts);
      DKV.put(getMSBHeaderKey(msb, H2O.SELF.index()), msbh);
      for (int b=0;b<_o[msb].length; ++b) {
        OXWrapper oxPair = new OXWrapper(_o[msb][b], _x[msb][b]);
        DKV.put(getIndexKeyForMSB(msb, b, H2O.SELF.index()), oxPair);
      }
    }
  }
}

// It is intended that several of these SingleThreadRadixOrder run on the same node, to utilize the cores available.
// The initial MSB needs to split by num nodes * cpus per node; e.g. 256 is pretty good for 10 nodes of 32 cores. Later, use 9 bits, or a few more bits accordingly.
// Its this 256 * 4kB = 1MB that needs to be < cache per core for cache write efficiency in MoveByFirstByte(). 10 bits (1024 threads) would be 4MB which still < L2
// Since o[] and x[] are arrays here (not Vecs) it's harder to see how to parallelize inside this function. Therefore avoid that issue by using more threads in calling split.
// General principle here is that several parallel, tight, branch free loops, faster than one heavy DKV pass per row

class SingleThreadRadixOrder extends DTask<SingleThreadRadixOrder> {
  public long _o[][]; // output.  The gathered _o from all the nodes and concatenated in order of chunk number
  public byte _x[][]; // same
  long _otmp[][];
  byte _xtmp[][];

  // TEMPs
  long _counts[][];
  byte keytmp[];
  //public long _groupSizes[][];

  long _nGroup[], _len;
  int _MSBvalue;  // only needed to be able to return the number of groups back to the caller RadixOrder
  int _keySize, _batchSize;

  // outputs ...
  // o and x are changed in-place always
  // iff _groupsToo==true then the following are allocated and returned
  //int _MSBvalue;  // Most Significant Byte value this node is working on
  //long _nGroup[/*MSBvalue*/];  // number of groups found (could easily be > BATCHLONG). Each group has its size stored in _groupSize.
  //long _groupSize[/*MSBvalue*/][/*_nGroup div BATCHLONG*/][/*mod*/];

  // Now taken out ... boolean groupsToo, long groupSize[][][], long nGroup[], int MSBvalue
  //long _len;
  //int _byte;
  //int _keySize;   // bytes

  SingleThreadRadixOrder(Frame fr, int batchSize, int keySize, long nGroup[], int MSBvalue) {
    MoveByFirstByte.MSBHeader[] MSBnodeHeader = new MoveByFirstByte.MSBHeader[H2O.CLOUD.size()];
    long numRows=0;
    for (int n=0; n<H2O.CLOUD.size(); ++n) {
      MSBnodeHeader[n] = DKV.getGet(MoveByFirstByte.getMSBHeaderKey(MSBvalue, n));
      if (MSBnodeHeader[n]==null) continue;
      numRows += ArrayUtils.sum(MSBnodeHeader[n]._MSBnodeChunkCounts);   // This numRows is split into nbatch batches on that node.
      // This header also has the counts of each chunk (the ordered chunk numbers on that node)
    }
    if (numRows == 0) return;

    // Allocate final _o and _x for this MSB which is gathered together on this node from the other nodes.
    int nbatch = (int) (numRows -1) / batchSize +1;   // at least one batch.    TO DO:  as Arno suggested, wrap up into class for fixed width batching (to save espc overhead)
    int lastSize = (int) (numRows - (nbatch-1) * _batchSize);   // the size of the last batch (could be batchSize, too if happens to be exact multiple of batchSize)
    _o = new long[nbatch][];
    _x = new byte[nbatch][];
    int b;
    for (b = 0; b < nbatch-1; b++) {
      _o[b] = new long[_batchSize];          // TO DO?: use MemoryManager.malloc8()
      _x[b] = new byte[_batchSize * _keySize];
    }
    _o[b] = new long[lastSize];
    _x[b] = new byte[lastSize * _keySize];

    MoveByFirstByte.OXWrapper MSBnodeCurrentOX[] = new MoveByFirstByte.OXWrapper[H2O.CLOUD.size()];
    int MSBnodeCurrentBatch[] = new int[H2O.CLOUD.size()];  // which batch of OX are we on from that node?  Initialized to 0.
    for (int node=0; node<H2O.CLOUD.size(); node++) {
      MSBnodeCurrentOX[node] = DKV.getGet(MoveByFirstByte.getIndexKeyForMSB(MSBvalue, /*batch=*/0, node));   // get the first batch for each node for this MSB
    }
    int MSBnodeCurrentOffset[] = new int[H2O.CLOUD.size()];
    int MSBnodeCurrentChunkIdx[] = new int[H2O.CLOUD.size()];  // that node has n chunks and which of those are we currently on?

    int targetBatch = 0, targetOffset = 0, targetBatchRemaining = batchSize;

    for (int c=0; c<fr.anyVec().nChunks(); c++) {   // TO DO: put this into class as well, to ArrayCopy into batched
      int fromNode = fr.anyVec().chunkKey(c).home_node().index();
      int numRowsToCopy = MSBnodeHeader[fromNode]._MSBnodeChunkCounts[MSBnodeCurrentChunkIdx[fromNode]++];
      int sourceBatchRemaining = batchSize - MSBnodeCurrentOffset[fromNode];    // at most batchSize remaining.  No need to actually put the number of rows left in here
      if (sourceBatchRemaining <= numRowsToCopy) {
        if (targetBatchRemaining <= sourceBatchRemaining) {
          System.arraycopy(MSBnodeCurrentOX[fromNode]._o, MSBnodeCurrentOffset[fromNode], _o[targetBatch], targetOffset, targetBatchRemaining);
          System.arraycopy(MSBnodeCurrentOX[fromNode]._x, MSBnodeCurrentOffset[fromNode]*_keySize, _x[targetBatch], targetOffset*_keySize, targetBatchRemaining*_keySize);
          MSBnodeCurrentOffset[fromNode] += targetBatchRemaining;
          sourceBatchRemaining -= targetBatchRemaining;
          assert sourceBatchRemaining >= 0;
          numRowsToCopy -= targetBatchRemaining;
          targetBatch++;
          targetOffset = 0;
          targetBatchRemaining = batchSize;
          assert targetBatchRemaining >= sourceBatchRemaining;
        }
        if (sourceBatchRemaining <= numRowsToCopy) {
          System.arraycopy(MSBnodeCurrentOX[fromNode]._o, MSBnodeCurrentOffset[fromNode], _o[targetBatch], targetOffset, sourceBatchRemaining);
          System.arraycopy(MSBnodeCurrentOX[fromNode]._x, MSBnodeCurrentOffset[fromNode]*_keySize, _x[targetBatch], targetOffset*_keySize, sourceBatchRemaining*_keySize);
          numRowsToCopy -= sourceBatchRemaining;
          targetOffset += sourceBatchRemaining;
          targetBatchRemaining -= sourceBatchRemaining;
          // TO DO:  delete and free MSBnodeCurrentBatch[fromNode].  Have used it all now.
          // get the next batch from the node ...
          MSBnodeCurrentOX[fromNode] = DKV.getGet(MoveByFirstByte.getIndexKeyForMSB(MSBvalue, ++MSBnodeCurrentBatch[fromNode], fromNode));
          MSBnodeCurrentOffset[fromNode] = 0;
          sourceBatchRemaining = batchSize;
        }
        assert sourceBatchRemaining >= numRowsToCopy;
      }
      if (targetBatchRemaining <= numRowsToCopy) {
        System.arraycopy(MSBnodeCurrentOX[fromNode]._o, MSBnodeCurrentOffset[fromNode], _o[targetBatch], targetOffset, targetBatchRemaining);
        System.arraycopy(MSBnodeCurrentOX[fromNode]._x, MSBnodeCurrentOffset[fromNode]*_keySize, _x[targetBatch], targetOffset*_keySize, targetBatchRemaining*_keySize);
        MSBnodeCurrentOffset[fromNode] += targetBatchRemaining;
        sourceBatchRemaining -= targetBatchRemaining;
        assert sourceBatchRemaining >= 0;
        numRowsToCopy -= targetBatchRemaining;
        targetBatch++;
        targetOffset = 0;
        targetBatchRemaining = batchSize;
        assert targetBatchRemaining >= numRowsToCopy;
      }
      if (numRowsToCopy > 0) {
        assert targetBatchRemaining > numRowsToCopy;
        System.arraycopy(MSBnodeCurrentOX[fromNode]._o, MSBnodeCurrentOffset[fromNode], _o[targetBatch], targetOffset, numRowsToCopy);
        System.arraycopy(MSBnodeCurrentOX[fromNode]._x, MSBnodeCurrentOffset[fromNode]*_keySize, _x[targetBatch], targetOffset*_keySize, numRowsToCopy*_keySize);
        MSBnodeCurrentOffset[fromNode] += numRowsToCopy;
        sourceBatchRemaining -= numRowsToCopy;
        assert sourceBatchRemaining > 0;
        targetOffset += numRowsToCopy;
        targetBatchRemaining -= numRowsToCopy;
      }
    }
    _xtmp = new byte[_x.length][];
    _otmp = new long[_o.length][];
    assert _x.length == _o.length;  // i.e. aligned batch size between x and o (think 20 bytes keys and 8 bytes of long in o)
    for (int i=0; i<_x.length; i++) {    // Seems like no deep clone available in Java. Maybe System.arraycopy but maybe that needs target to be allocated first
      _xtmp[i] = Arrays.copyOf(_x[i], _x[i].length);
      _otmp[i] = Arrays.copyOf(_o[i], _o[i].length);
    }
    _batchSize = batchSize;
    _keySize = keySize;
    _nGroup = nGroup;
    _MSBvalue = MSBvalue;
    keytmp = new byte[_keySize];
    _counts = new long[keySize][256];
    // TO DO: a way to share this working memory between threads.
    //        Just create enough for the 4 threads active at any one time.  Not 256 allocations and releases.
    //        We need o[] and x[] in full for the result. But this way we don't need full size xtmp[] and otmp[] at any single time.
    //        Currently Java will allocate and free these xtmp and otmp and maybe it does good enough job reusing heap that we don't need to explicitly optimize this reuse.
    //        Perhaps iterating this task through the largest bins first will help java reuse heap.
    //_groupSizes = new long[256][];
    //_groups = groups;
    //_nGroup = nGroup;
    //_whichGroup = whichGroup;
    //_groups[_whichGroup] = new long[(int)Math.min(MAXVECLONG, len) ];   // at most len groups (i.e. all groups are 1 row)
  }
  @Override protected void compute2() {
    if (_len != 0) {
      run(0, _len, _keySize-1);  // if keySize is 6 bytes, first byte is byte 5
    }
    _counts = null;
    keytmp = null;
    _nGroup = null;
    tryComplete();
  }

  int keycmp(byte x[], int xi, byte y[], int yi) {
    // Same return value as strcmp in C. <0 => xi<yi
    xi *= _keySize; yi *= _keySize;
    int len = _keySize;
    while (len > 1 && x[xi] == y[yi]) { xi++; yi++; len--; }
    return ((x[xi] & 0xFF) - (y[yi] & 0xFF)); // 0xFF for getting back from -1 to 255
  }

  public void insert(long start, int len)   // only for small len so len can be type int
/*  orders both x and o by reference in-place. Fast for small vectors, low overhead.
    don't be tempted to binsearch backwards here because have to shift anyway  */
  {
    int batch0 = (int) (start / _batchSize);
    int batch1 = (int) (start+len-1) / _batchSize;
    assert batch0==0;
    _nGroup[_MSBvalue]++;  // This is at least 1 group (if all keys in this len items are equal)
    if (batch0 == batch1)  {
      // Within the same batch. Likely very often since len<=200
      byte _xbatch[] = _x[batch0];  // taking this outside the loop does indeed make quite a big different (hotspot isn't catching this, then)
      long _obatch[] = _o[batch0];
      int offset = (int) (start % _batchSize);
      for (int i=1; i<len; i++) {
        int cmp = keycmp(_xbatch, offset+i, _xbatch, offset+i-1);  // TO DO: we don't need to compare the whole key here.  Set cmpLen < keySize
        if (cmp < 0) {
          System.arraycopy(_xbatch, (offset+i)*_keySize, keytmp, 0, _keySize);
          int j = i - 1;
          long otmp = _obatch[offset+i];
          do {
            System.arraycopy(_xbatch, (offset+j)*_keySize, _xbatch, (offset+j+1)*_keySize, _keySize);
            _obatch[offset+j+1] = _obatch[offset+j];
            j--;
          } while (j >= 0 && (cmp = keycmp(keytmp, 0, _xbatch, offset+j))<0);
          System.arraycopy(keytmp, 0, _xbatch, (offset+j+1)*_keySize, _keySize);
          _obatch[offset + j + 1] = otmp;
        }
        if (cmp>0) _nGroup[_MSBvalue]++;   // Saves sweep afterwards. Possible now that we don't maintain the group sizes in this deep pass, unlike data.table
        // saves call to push() and hop to _groups
        // _nGroup == nrow afterwards tells us if the keys are unique.
        // Sadly, it seems _nGroup += (cmp==0) isn't possible in Java even with explicit cast of boolean to int, so branch needed
      }
    } else {
      assert batch0 == batch1-1;
      throw H2O.unimpl();
      // TO DO: copy two halves to contiguous temp memory, do the stuff above and then split it back to the two batches afterwards.
      // Straddles batches very rarely (at most once per batch) so no speed impact at all.
    }
  }

  public void run(long start, long len, int Byte) {

    // System.out.println("run " + start + " " + len + " " + Byte);
    if (len < 200) {               // N_SMALL=200 is guess based on limited testing. Needs calibrate().
      // Was 50 based on sum(1:50)=1275 worst -vs- 256 cummulate + 256 memset + allowance since reverse order is unlikely.
      insert(start, (int)len);   // when nalast==0, iinsert will be called only from within iradix.
      // TO DO:  inside insert it doesn't need to compare the bytes so far as they're known equal,  so pass Byte (NB: not Byte-1) through to insert()
      // TO DO:  Maybe transposing keys to be a set of _keySize byte columns might in fact be quicker - no harm trying. What about long and varying length string keys?
      return;
    }
    int batch0 = (int) (start / _batchSize);
    int batch1 = (int) (start+len-1) / _batchSize;
    assert batch0==0;
    assert batch0==batch1;  // TO DO: count across batches of 2Bn.  Wish we had 64bit indexing in Java.
    byte _xbatch[] = _x[batch0];  // taking this outside the loop does indeed make quite a big different (hotspot isn't catching this, then)
    long thisHist[] = _counts[Byte];
    // thisHist reused and carefully set back to 0 below so we don't need to clear it now
    int idx = (int)start*_keySize + _keySize-Byte-1;
    int bin=-1;  // the last bin incremented. Just to see if there is only one bin with a count.
    for (long i = 0; i < len; i++) {
      bin = 0xff & _xbatch[idx];
      thisHist[bin]++;
      idx += _keySize;
      // maybe TO DO:  shorten key by 1 byte on each iteration, so we only need to thisx && 0xFF.  No, because we need for construction of final table key columns.
    }
    if (thisHist[bin] == len) {
      // one bin has count len and the rest zero => next byte quick
      thisHist[bin] = 0;  // important, clear for reuse
      if (Byte == 0) _nGroup[_MSBvalue]++;
      else run(start, len, Byte - 1);
      return;
    }
    long rollSum = 0;
    for (int c = 0; c < 256; c++) {
      long tmp = thisHist[c];
      if (tmp == 0) continue;  // important to skip zeros for logic below to undo cumulate. Worth the branch to save a deeply iterative memset back to zero
      thisHist[c] = rollSum;
      rollSum += tmp;
    }
    long _obatch[] = _o[batch0];
    byte _xtmpbatch[] = _xtmp[batch0];  // TO DO- ignoring >2^31 for now.  No batching.
    long _otmpbatch[] = _otmp[batch0];
    idx = (int)start*_keySize + _keySize-Byte-1;
    for (int i = 0; i < len; i++) {
      long target = thisHist[0xff & _xbatch[idx]]++;
      _otmpbatch[(int)(target)] = _obatch[(int)start+i];   // this must be kept in 8 bytes longs
      System.arraycopy( _xbatch, ((int)start+i)*_keySize, _xtmpbatch, (int)target*_keySize, _keySize );
      idx += _keySize;
      //  Maybe TO DO:  this can be variable byte width and smaller widths as descend through bytes (TO DO: reverse byte order so always doing &0xFF)
    }

    System.arraycopy(_otmpbatch, 0, _obatch, (int)start, (int)len);  // always use the beginning of _otmp and _xtmp just to reuse the first hot pages
    System.arraycopy(_xtmpbatch, 0, _xbatch, (int)start*_keySize, (int)len*_keySize);

    long itmp = 0;
    for (int i=0; i<256; i++) {
      if (thisHist[i]==0) continue;
      long thisgrpn = thisHist[i] - itmp;
      if (thisgrpn == 1 || Byte == 0) {
        _nGroup[_MSBvalue]++;
      } else {
        run(start+itmp, thisgrpn, Byte-1);
      }
      itmp = thisHist[i];
      thisHist[i] = 0;  // important, to save clearing counts on next iteration
    }
  }
  // Push gathered and sorted o/x (the final index) to DKV for use by Merge, Grouping etc.
  @Override protected void postLocal() {
    int nc = _fr.anyVec().nChunks();
    for (int msb =0; msb <_o.length /*256*/; ++msb) {
      if(_o[msb] == null) continue;
      int numChunks = 0;  // how many of the chunks on this node had some rows with this MSB
      for (int c=0; c<nc; c++) if (_counts[c][msb] > 0) numChunks++;
      int MSBnodeChunkCounts[] = new int[numChunks];   // make dense.  And by construction (i.e. cumulative counts) these chunks contributed in order
      int j=0;
      for (int c=0; c<nc; c++) if (_counts[c][msb] > 0) MSBnodeChunkCounts[j++] = (int)_counts[c][msb];  // _counts is long so it can be accumulated in-place I think.  TO DO: check
      assert _MSBhist[msb] == ArrayUtils.sum(MSBnodeChunkCounts);
      MSBHeader msbh = new MSBHeader(MSBnodeChunkCounts);
      DKV.put(getMSBHeaderKey(msb, H2O.SELF.index()), msbh);
      for (int b=0;b<_o[msb].length; ++b) {
        OXWrapper oxPair = new OXWrapper(_o[msb][b], _x[msb][b]);
        DKV.put(getIndexKeyForMSB(msb, b, H2O.SELF.index()), oxPair);
      }
    }
  }
}

public class RadixOrder {
  private static final int MAXVECBYTE = 1073741824;   // not 2^31 because that needs long to store it (could do)
  int _biggestBit[];
  int _bytesUsed[];
  long[][][] _o;
  byte[][][] _x;

  RadixOrder(Frame DF, int whichCols[]) {
    System.out.println("Calling RadixCount ...");
    long t0 = System.nanoTime();
    _biggestBit = new int[whichCols.length];   // currently only biggestBit[0] is used
    _bytesUsed = new int[whichCols.length];
    //long colMin[] = new long[whichCols.length];
    for (int i = 0; i < whichCols.length; i++) {
      Vec col = DF.vec(whichCols[i]);
      //long range = (long) (col.max() - col.min());
      //assert range >= 1;   // otherwise log(0)==-Inf next line
      _biggestBit[i] = 1 + (int) Math.floor(Math.log(col.max()) / Math.log(2));   // number of bits starting from 1 easier to think about (for me)
      _bytesUsed[i] = (int) Math.ceil(_biggestBit[i] / 8.0);
      //colMin[i] = (long) col.min();   // TO DO: non-int/enum
    }
    new RadixCount(DF._key, _biggestBit[0], whichCols[0]).doAll(DF.vec(whichCols[0]));
    System.out.println("Time of MSB count: " + (System.nanoTime() - t0) / 1e9);

    // Create final result. TO DO: take this multi-node and leave it on each node
    //_returnList = new ArrayList<>();

    int keySize = ArrayUtils.sum(_bytesUsed);   // The MSB is stored (seemingly wastefully on first glance) because we need it when aligning two keys in Merge()
    int batchSize = MAXVECBYTE / Math.max(keySize, 8);
    // The Math.max ensures that batches of o and x are aligned, even for wide keys. To save % and / in deep iteration; e.g. in insert().

    System.out.println("Time to allocate o[][] and x[][]: " + (System.nanoTime() - t0) / 1e9);
    t0 = System.nanoTime();
    // NOT TO DO:  we do need the full allocation of x[] and o[].  We need o[] anyway.  x[] will be compressed and dense.
    // o is the full ordering vector of the right size
    // x is the byte key aligned with o
    // o AND x are what bmerge() needs. Pushing x to each node as well as o avoids inter-node comms.
    new MoveByFirstByte(DF._key, _biggestBit[0], keySize, batchSize, _bytesUsed, whichCols).doAll(DF.vecs(whichCols));   // postLocal needs DKV.put()
    System.out.println("Time to MoveByFirstByte: " + (System.nanoTime() - t0) / 1e9);
    t0 = System.nanoTime();

    long nGroup[] = new long[257];   // one extra for later to make undo of cumulate easier when finding groups.  TO DO: let grouper do that and simplify here to 256

    Futures fs = new Futures();
    _o = new long[256][][];
    _x = new byte[256][][];
    for (int i = 0; i < 256; i++) {
      H2ONode node = MoveByFirstByte.ownerOfMSB(i);
      SingleThreadRadixOrder radixOrder = new RPC<>(node, new SingleThreadRadixOrder(DF, batchSize, keySize, nGroup, i)).call().get(); //TODO: make async
      _o[i] = radixOrder._o;
      _x[i] = radixOrder._x;
    }
    fs.blockForPending();
    System.out.println("Time for all calls to SingleThreadRadixOrder: " + (System.nanoTime() - t0) / 1e9);

    // If sum(nGroup) == nrow then the index is unique.
    // 1) useful to know if an index is unique or not (when joining to it we know multiples can't be returned so can allocate more efficiently)
    // 2) If all groups are size 1 there's no need to actually allocate an all-1 group size vector (perhaps user was checking for uniqueness by counting group sizes)
    // 3) some nodes may have unique input and others may contain dups; e.g., in the case of looking for rare dups.  So only a few threads may have found dups.
    // 4) can sweep again in parallel and cache-efficient finding the groups, and allocate known size up front to hold the group sizes.
    // 5) can return to Flow early with the group count. User may now realise they selected wrong columns and cancel early.

  }
}
