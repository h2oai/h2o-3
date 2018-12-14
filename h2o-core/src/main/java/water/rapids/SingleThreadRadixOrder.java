package water.rapids;

// General principle here is that several parallel, tight, branch free loops,
// faster than one heavy DKV pass per row

// It is intended that several of these SingleThreadRadixOrder run on the same
// node, to utilize the cores available.  The initial MSB needs to split by num
// nodes * cpus per node; e.g. 256 is pretty good for 10 nodes of 32 cores.
// Later, use 9 bits, or a few more bits accordingly.

// Its this 256 * 4kB = 1MB that needs to be < cache per core for cache write
// efficiency in MoveByFirstByte().  10 bits (1024 threads) would be 4MB which
// still < L2

// Since o[] and x[] are arrays here (not Vecs) it's harder to see how to
// parallelize inside this function. Therefore avoid that issue by using more
// threads in calling split.

import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;

import java.util.Arrays;

class SingleThreadRadixOrder extends DTask<SingleThreadRadixOrder> {
  private final Frame _fr;
  private final int _MSBvalue;  // only needed to be able to return the number of groups back to the caller RadixOrder
  private final int _keySize, _batchSize;
  private final boolean _isLeft;

  private transient long _o[/*batch*/][];
  private transient byte _x[/*batch*/][];
  private transient long _otmp[][];
  private transient byte _xtmp[][];

  // TEMPs
  private transient long counts[][];
  private transient byte keytmp[];
  //public long _groupSizes[][];


  // outputs ...
  // o and x are changed in-place always
  // iff _groupsToo==true then the following are allocated and returned

  SingleThreadRadixOrder(Frame fr, boolean isLeft, int batchSize, int keySize, /*long nGroup[],*/ int MSBvalue) {
    _fr = fr;
    _isLeft = isLeft;
    _batchSize = batchSize;
    _keySize = keySize;
    _MSBvalue = MSBvalue;
  }

  @Override
  public void compute2() {
    keytmp = MemoryManager.malloc1(_keySize);
    counts = new long[_keySize][256];
    Key k;

    SplitByMSBLocal.MSBNodeHeader[] MSBnodeHeader = new SplitByMSBLocal.MSBNodeHeader[H2O.CLOUD.size()];
    long numRows =0;
    for (int n=0; n<H2O.CLOUD.size(); n++) {
      // Log.info("Getting MSB " + MSBvalue + " Node Header from node " + n + "/" + H2O.CLOUD.size() + " for Frame " + _fr._key);
      // Log.info("Getting");
      k = SplitByMSBLocal.getMSBNodeHeaderKey(_isLeft, _MSBvalue, n);
      MSBnodeHeader[n] = DKV.getGet(k);
      if (MSBnodeHeader[n]==null) continue;
      DKV.remove(k);
      numRows += ArrayUtils.sum(MSBnodeHeader[n]._MSBnodeChunkCounts);   // This numRows is split into nbatch batches on that node.
      // This header has the counts of each chunk (the ordered chunk numbers on that node)
    }
    if (numRows == 0) { tryComplete(); return; }

    // Allocate final _o and _x for this MSB which is gathered together on this
    // node from the other nodes.
    // TO DO: as Arno suggested, wrap up into class for fixed width batching
    // (to save espc overhead)
    int nbatch = (int) ((numRows-1) / _batchSize +1);   // at least one batch.
    // the size of the last batch (could be batchSize, too if happens to be
    // exact multiple of batchSize)
    int lastSize = (int) (numRows - (nbatch-1)*_batchSize);
    _o = new long[nbatch][];
    _x = new byte[nbatch][];
    int b;
    for (b = 0; b < nbatch-1; b++) {
      _o[b] = MemoryManager.malloc8(_batchSize);          // TO DO?: use MemoryManager.malloc8()
      _x[b] = MemoryManager.malloc1(_batchSize * _keySize);
    }
    _o[b] = MemoryManager.malloc8(lastSize);
    _x[b] = MemoryManager.malloc1(lastSize * _keySize);

    SplitByMSBLocal.OXbatch ox[/*node*/] = new SplitByMSBLocal.OXbatch[H2O.CLOUD.size()];
    int oxBatchNum[/*node*/] = new int[H2O.CLOUD.size()];  // which batch of OX are we on from that node?  Initialized to 0.
    for (int node=0; node<H2O.CLOUD.size(); node++) {  //TO DO: why is this serial?  Relying on
      k = SplitByMSBLocal.getNodeOXbatchKey(_isLeft, _MSBvalue, node, /*batch=*/0);
      // assert k.home();   // TODO: PUBDEV-3074
      ox[node] = DKV.getGet(k);   // get the first batch for each node for this MSB
      DKV.remove(k);
    }
    int oxOffset[] = MemoryManager.malloc4(H2O.CLOUD.size());
    int oxChunkIdx[] = MemoryManager.malloc4(H2O.CLOUD.size());  // that node has n chunks and which of those are we currently on?

    int targetBatch = 0, targetOffset = 0, targetBatchRemaining = _batchSize;
    final Vec vec = _fr.anyVec();
    assert vec != null;
    for (int c=0; c<vec.nChunks(); c++) {
      int fromNode = vec.chunkKey(c).home_node().index();  // each chunk in the column may be on different nodes
      // See long comment at the top of SendSplitMSB. One line from there repeated here :
      // " When the helper node (i.e. this one, now) (i.e the node doing all
      // the A's) gets the A's from that node, it must stack all the nodes' A's
      // with the A's from the other nodes in chunk order in order to maintain
      // the original order of the A's within the global table. "
      // TODO: We could process these in node order and or/in parallel if we
      // cumulated the counts first to know the offsets - should be doable and
      // high value
      if (MSBnodeHeader[fromNode] == null) continue;
      // magically this works, given the outer for loop through global
      // chunk.  Relies on LINE_ANCHOR_1 above.
      int numRowsToCopy = MSBnodeHeader[fromNode]._MSBnodeChunkCounts[oxChunkIdx[fromNode]++];   
      // _MSBnodeChunkCounts is a vector of the number of contributions from
      // each Vec chunk.  Since each chunk is length int, this must less than
      // that, so int The set of data corresponding to the Vec chunk
      // contributions is stored packed in batched vectors _o and _x.

      // at most batchSize remaining.  No need to actually put the number of rows left in here
      int sourceBatchRemaining = _batchSize - oxOffset[fromNode];
      while (numRowsToCopy > 0) {   // No need for class now, as this is a bit different to the other batch copier. Two isn't too bad.
        int thisCopy = Math.min(numRowsToCopy, Math.min(sourceBatchRemaining, targetBatchRemaining));
        System.arraycopy(ox[fromNode]._o, oxOffset[fromNode],          _o[targetBatch], targetOffset,          thisCopy);
        System.arraycopy(ox[fromNode]._x, oxOffset[fromNode]*_keySize, _x[targetBatch], targetOffset*_keySize, thisCopy*_keySize);
        numRowsToCopy -= thisCopy;
        oxOffset[fromNode] += thisCopy; sourceBatchRemaining -= thisCopy;
        targetOffset += thisCopy; targetBatchRemaining -= thisCopy;
        if (sourceBatchRemaining == 0) {
          // fetch the next batch :
          k = SplitByMSBLocal.getNodeOXbatchKey(_isLeft, _MSBvalue, fromNode, ++oxBatchNum[fromNode]);
          assert k.home();
          ox[fromNode] = DKV.getGet(k);
          DKV.remove(k);
          if (ox[fromNode] == null) {
            // if the last chunksworth fills a batchsize exactly, the getGet above will have returned null.
            // TODO: Check will Cliff that a known fetch of a non-existent key is ok e.g. won't cause a delay/block? If ok, leave as good check.
            int numNonZero = 0; for (int tmp : MSBnodeHeader[fromNode]._MSBnodeChunkCounts) if (tmp>0) numNonZero++;
            assert oxBatchNum[fromNode]==numNonZero;
            assert ArrayUtils.sum(MSBnodeHeader[fromNode]._MSBnodeChunkCounts) % _batchSize == 0;
          }
          oxOffset[fromNode] = 0;
          sourceBatchRemaining = _batchSize;
        }
        if (targetBatchRemaining == 0) {
          targetBatch++;
          targetOffset = 0;
          targetBatchRemaining = _batchSize;
        }
      }
    }

    // We now have _o and _x collated from all the contributing nodes, in the correct original order.
    // TODO save this allocation and reuse per thread?  Or will heap just take care of it. Time this allocation and copy as step 1 anyway.
    _xtmp = new byte[_x.length][];
    _otmp = new long[_o.length][];
    assert _x.length == _o.length;  // i.e. aligned batch size between x and o (think 20 bytes keys and 8 bytes of long in o)
    // Seems like no deep clone available in Java. Maybe System.arraycopy but
    // maybe that needs target to be allocated first
    for (int i=0; i<_x.length; i++) {    
      _xtmp[i] = Arrays.copyOf(_x[i], _x[i].length);
      _otmp[i] = Arrays.copyOf(_o[i], _o[i].length);
    }
    // TO DO: a way to share this working memory between threads.
    //        Just create enough for the 4 threads active at any one time.  Not 256 allocations and releases.
    //        We need o[] and x[] in full for the result. But this way we don't need full size xtmp[] and otmp[] at any single time.
    //        Currently Java will allocate and free these xtmp and otmp and maybe it does good enough job reusing heap that we don't need to explicitly optimize this reuse.
    //        Perhaps iterating this task through the largest bins first will help java reuse heap.
    assert(_o != null);
    assert(numRows > 0);

    // The main work. Radix sort this batch ...
    run(0, numRows, _keySize-1);  // if keySize is 6 bytes, first byte is byte 5

    // don't need to clear these now using private transient
    // _counts = null;
    // keytmp = null;
    //_nGroup = null;

    // tell the world how many batches and rows for this MSB
    OXHeader msbh = new OXHeader(_o.length, numRows, _batchSize);
    Futures fs = new Futures();
    DKV.put(getSortedOXHeaderKey(_isLeft, _MSBvalue), msbh, fs, true);
    assert _o.length == _x.length;
    for (b=0; b<_o.length; b++) {
      SplitByMSBLocal.OXbatch tmp = new SplitByMSBLocal.OXbatch(_o[b], _x[b]);
      Value v = new Value(SplitByMSBLocal.getSortedOXbatchKey(_isLeft, _MSBvalue, b), tmp);
      DKV.put(v._key, v, fs, true);  // the OXbatchKey's on this node will be reused for the new keys
      v.freeMem();
    }
    // TODO: check numRows is the total of the _x[b] lengths
    fs.blockForPending();
    tryComplete();
  }

  static Key getSortedOXHeaderKey(boolean isLeft, int MSBvalue) {
    // This guy has merges together data from all nodes and its data is not "from" 
    // any particular node.  Therefore node number should not be in the key.
    return Key.make("__radix_order__SortedOXHeader_MSB" + MSBvalue + (isLeft ? "_LEFT" : "_RIGHT"));  // If we don't say this it's random ... (byte) 1 /*replica factor*/, (byte) 31 /*hidden user-key*/, true, H2O.SELF);
  }

  static class OXHeader extends Iced<OXHeader> {
    OXHeader(int batches, long numRows, int batchSize) { _nBatch = batches; _numRows = numRows; _batchSize = batchSize; }
    final int _nBatch;
    final long _numRows;
    final int _batchSize;
  }

  private int keycmp(byte x[], int xi, byte y[], int yi) {
    // Same return value as strcmp in C. <0 => xi<yi
    xi *= _keySize; yi *= _keySize;
    int len = _keySize;
    while (len > 1 && x[xi] == y[yi]) { xi++; yi++; len--; }
    return ((x[xi] & 0xFF) - (y[yi] & 0xFF)); // 0xFF for getting back from -1 to 255
  }

  // orders both x and o by reference in-place.  Fast for small vectors, low
  // overhead.  don't be tempted to binsearch backwards here because have to
  // shift anyway
  public void insert(long start, /*only for small len so len can be type int*/int len) {
    int batch0 = (int) (start / _batchSize);
    int batch1 = (int) ((start+len-1) / _batchSize);
    long origstart = start;   // just for when straddle batch boundaries
    int len0 = 0;             // same
    byte _xbatch[];
    long _obatch[];
    if (batch1 != batch0) {
      // small len straddles a batch boundary. Unlikely very often since len<=200
      assert batch0 == batch1-1;
      len0 = _batchSize - (int)(start % _batchSize);
      // copy two halves to contiguous temp memory, do the below, then split it back to the two halves afterwards.
      // Straddles batches very rarely (at most once per batch) so no speed impact at all.
      _xbatch = new byte[len * _keySize];
      System.arraycopy(_x[batch0], (int)((start % _batchSize)*_keySize),_xbatch, 0,  len0*_keySize);
      System.arraycopy( _x[batch1], 0,_xbatch, len0*_keySize, (len-len0)*_keySize);
      _obatch = new long[len];
      System.arraycopy(_o[batch0], (int)(start % _batchSize), _obatch, 0, len0);
      System.arraycopy(_o[batch1], 0, _obatch, len0, len-len0);
      start = 0;
    } else {
      _xbatch = _x[batch0];  // taking this outside the loop does indeed make quite a big different (hotspot isn't catching this, then)
      _obatch = _o[batch0];
    }
    int offset = (int) (start % _batchSize);
    for (int i=1; i<len; i++) {
      int cmp = keycmp(_xbatch, offset+i, _xbatch, offset+i-1);  // TO DO: we don't need to compare the whole key here.  Set cmpLen < keySize
      if (cmp < 0) {
        System.arraycopy(_xbatch, (offset+i)*_keySize, keytmp, 0, _keySize);
        int j = i-1;
        long otmp = _obatch[offset+i];
        do {
          System.arraycopy(_xbatch, (offset+j)*_keySize, _xbatch, (offset+j+1)*_keySize, _keySize);
          _obatch[offset+j+1] = _obatch[offset+j];
          j--;
        } while (j >= 0 && keycmp(keytmp, 0, _xbatch, offset+j)<0);
        System.arraycopy(keytmp, 0, _xbatch, (offset+j+1)*_keySize, _keySize);
        _obatch[offset + j + 1] = otmp;
      }
    }
    if (batch1 != batch0) {
      // Put the sorted data back into original two places straddling the boundary
      System.arraycopy(_xbatch, 0,_x[batch0], (int)(origstart % _batchSize) *_keySize,  len0*_keySize);
      System.arraycopy(_xbatch, len0*_keySize,_x[batch1], 0,  (len-len0)*_keySize);
      System.arraycopy( _obatch, 0,_o[batch0], (int)(origstart % _batchSize), len0);
      System.arraycopy(_obatch, len0,_o[batch1], 0,  len-len0);
    }
  }

  public void run(final long start, final long len, final int Byte) {
    if (len < 200) { // N_SMALL=200 is guess based on limited testing. Needs calibrate().
      // Was 50 based on sum(1:50)=1275 worst -vs- 256 cummulate + 256 memset +
      // allowance since reverse order is unlikely.
      insert(start, (int)len);   // when nalast==0, iinsert will be called only from within iradix.
      // TO DO: inside insert it doesn't need to compare the bytes so far as
      // they're known equal, so pass Byte (NB: not Byte-1) through to insert()

      // TO DO: Maybe transposing keys to be a set of _keySize byte columns
      // might in fact be quicker - no harm trying. What about long and varying
      // length string keys?
      return;
    }
    final int batch0 = (int) (start / _batchSize);
    final int batch1 = (int) ((start+len-1) / _batchSize);
    // could well span more than one boundary when very large number of rows.
    final long thisHist[] = counts[Byte];
    // thisHist reused and carefully set back to 0 below so we don't need to clear it now
    int idx = (int)(start%_batchSize)*_keySize + _keySize-Byte-1;
    int bin=-1;  // the last bin incremented. Just to see if there is only one bin with a count.
    int thisLen = (int)Math.min(len, _batchSize - start%_batchSize);
    final int nbatch = batch1-batch0+1;  // number of batches this span of len covers.  Usually 1.  Minimum 1.
    for (int b=0; b<nbatch; b++) {
      // taking this outside the loop below does indeed make quite a big different (hotspot isn't catching this, then)
      byte _xbatch[] = _x[batch0+b];
      for (int i = 0; i < thisLen; i++) {
        bin = 0xff & _xbatch[idx];
        thisHist[bin]++;
        idx += _keySize;
        // maybe TO DO: shorten key by 1 byte on each iteration, so we only
        // need to thisx && 0xFF.  No, because we need for construction of
        // final table key columns.
      }
      idx = _keySize-Byte-1;
      thisLen = (b==nbatch-2/*next iteration will be last batch*/ ? (int)((start+len)%_batchSize) : _batchSize);
      // thisLen will be set to _batchSize for the middle batches when nbatch>=3
    }
    if (thisHist[bin] == len) {
      // one bin has count len and the rest zero => next byte quick
      thisHist[bin] = 0;  // important, clear for reuse
      if (Byte != 0)
        run(start, len, Byte-1);
      return;
    }
    long rollSum = 0;
    for (int c = 0; c < 256; c++) {
      if (rollSum == len) break;  // done, all other bins are zero, no need to loop through them all
      final long tmp = thisHist[c];
      // important to skip zeros for logic below to undo cumulate.  Worth the
      // branch to save a deeply iterative memset back to zero
      if (tmp == 0) continue;  
      thisHist[c] = rollSum;
      rollSum += tmp;
    }

    // Sigh. Now deal with batches here as well because Java doesn't have 64bit indexing.
    int oidx = (int)(start%_batchSize);
    int xidx = oidx*_keySize + _keySize-Byte-1;
    thisLen = (int)Math.min(len, _batchSize - start%_batchSize);
    for (int b=0; b<nbatch; b++) {
      // taking these outside the loop below does indeed make quite a big
      // different (hotspot isn't catching this, then)
      final long _obatch[] = _o[batch0+b];
      final byte _xbatch[] = _x[batch0+b];
      for (int i = 0; i < thisLen; i++) {
        long target = thisHist[0xff & _xbatch[xidx]]++;
        // now always write to the beginning of _otmp and _xtmp just to reuse the first hot pages
        _otmp[(int)(target/_batchSize)][(int)(target%_batchSize)] = _obatch[oidx+i];   // this must be kept in 8 bytes longs
        System.arraycopy(_xbatch, (oidx+i)*_keySize, _xtmp[(int)(target/_batchSize)], (int)(target%_batchSize)*_keySize, _keySize );
        xidx += _keySize;
        // Maybe TO DO: this can be variable byte width and smaller widths as
        // descend through bytes (TO DO: reverse byte order so always doing &0xFF)
      }
      xidx = _keySize-Byte-1;
      oidx = 0;
      thisLen = (b==nbatch-2/*next iteration will be last batch*/ ? (int)((start+len)%_batchSize) : _batchSize);
    }

    // now copy _otmp and _xtmp back over _o and _x from the start position, allowing for boundaries
    // _o, _x, _otmp and _xtmp all have the same _batchsize
    runCopy(start,len,_keySize,_batchSize,_otmp,_xtmp,_o,_x);

    long itmp = 0;
    for (int i=0; i<256; i++) {
      if (thisHist[i]==0) continue;
      final long thisgrpn = thisHist[i] - itmp;
      if( !(thisgrpn == 1 || Byte == 0) )
        run(start+itmp, thisgrpn, Byte-1);
      itmp = thisHist[i];
      thisHist[i] = 0;  // important, to save clearing counts on next iteration
    }
  }

  // Hot loop, pulled out from the main run code
  private static void runCopy(final long start, final long len, final int keySize, final int batchSize, final long otmp[][], final byte xtmp[][], final long o[][], final byte x[][]) {
    // now copy _otmp and _xtmp back over _o and _x from the start position, allowing for boundaries
    // _o, _x, _otmp and _xtmp all have the same _batchsize
    // Would be really nice if Java had 64bit indexing to save programmer time.
    long numRowsToCopy = len;
    int sourceBatch = 0, sourceOffset = 0;
    int targetBatch = (int)(start / batchSize), targetOffset = (int)(start % batchSize);
    int targetBatchRemaining = batchSize - targetOffset;  // 'remaining' means of the the full batch, not of the numRowsToCopy
    int sourceBatchRemaining = batchSize - sourceOffset;  // at most batchSize remaining.  No need to actually put the number of rows left in here
    while (numRowsToCopy > 0) {   // TO DO: put this into class as well, to ArrayCopy into batched
      final int thisCopy = (int)Math.min(numRowsToCopy, Math.min(sourceBatchRemaining, targetBatchRemaining));
      System.arraycopy(otmp[sourceBatch], sourceOffset,         o[targetBatch], targetOffset,         thisCopy);
      System.arraycopy(xtmp[sourceBatch], sourceOffset*keySize, x[targetBatch], targetOffset*keySize, thisCopy*keySize);
      numRowsToCopy -= thisCopy;
      sourceOffset += thisCopy; sourceBatchRemaining -= thisCopy;
      targetOffset += thisCopy; targetBatchRemaining -= thisCopy;
      if (sourceBatchRemaining == 0) { sourceBatch++; sourceOffset = 0; sourceBatchRemaining = batchSize; }
      if (targetBatchRemaining == 0) { targetBatch++; targetOffset = 0; targetBatchRemaining = batchSize; }
      // 'source' and 'target' deliberately the same length variable names and long lines deliberately used so we
      // can easy match them up vertically to ensure they are the same
    }
  }

}
