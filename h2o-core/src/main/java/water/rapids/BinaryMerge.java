package water.rapids;

// Since we have a single key field in H2O (different to data.table), bmerge() becomes a lot simpler (no
// need for recursion through join columns) with a downside of transfer-cost should we not need all the key.

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import static water.rapids.SingleThreadRadixOrder.getSortedOXHeaderKey;
import water.util.ArrayUtils;

import java.util.Arrays;

public class BinaryMerge extends DTask<BinaryMerge> {
  long _numRowsInResult=0;  // returned to caller, so not transient
  int _chunkSizes[];      // TODO:  only _chunkSizes.length is needed by caller, so return that length only
  double _timings[];

  transient long _retFirst[/*n2GB*/][];  // The row number of the first right table's index key that matches
  transient long _retLen[/*n2GB*/][];    // How many rows does it match to?
  transient byte _leftKey[/*n2GB*/][/*i mod 2GB * _keySize*/];
  transient byte _rightKey[][];
  transient long _leftOrder[/*n2GB*/][/*i mod 2GB * _keySize*/];
  transient long _rightOrder[][];
  transient boolean _oneToManyMatch = false;   // does any left row match to more than 1 right row?  If not, can allocate and loop more efficiently, and mark the resulting key'd frame with a 'unique' index.
                                               //   TODO: implement
  int _leftFieldSizes[], _rightFieldSizes[];   // the widths of each column in the key
  long _leftBase[], _rightBase[];        // the col.min() of each column in the key
  transient int _leftKeyNCol, _rightKeyNCol;   // the number of columns in the key i.e. length of _leftFieldSizes and _rightFieldSizes
  transient int _leftKeySize, _rightKeySize;   // the total width in bytes of the key, sum of field sizes
  transient int _numJoinCols;
  transient long _leftN, _rightN;
  transient long _leftFrom;
  transient int _retBatchSize;
  transient long _leftBatchSize, _rightBatchSize;
  Frame _leftFrame, _rightFrame;

  transient long _perNodeNumRightRowsToFetch[];
  transient long _perNodeNumLeftRowsToFetch[];
  int _leftMSB, _rightMSB;
  int _leftShift, _rightShift;

  boolean _allLeft, _allRight;

  Vec _leftVec, _rightVec;

  transient int _leftChunkNode[], _rightChunkNode[];  // fast lookups to save repeated calls to node.index() which calls binarysearch within it.

  BinaryMerge(Frame leftFrame, Frame rightFrame, int leftMSB, int rightMSB, int leftShift, int rightShift, int leftFieldSizes[], int rightFieldSizes[], long leftBase[], long rightBase[], boolean allLeft) {   // In X[Y], 'left'=i and 'right'=x
    _leftFrame = leftFrame;
    _rightFrame = rightFrame;
    _leftMSB = leftMSB;
    _rightMSB = rightMSB;
    _leftShift = leftShift;
    _rightShift = rightShift;
    _leftFieldSizes = leftFieldSizes; _rightFieldSizes = rightFieldSizes;
    _leftBase = leftBase; _rightBase = rightBase;
    _allLeft = allLeft;
    _allRight = false;  // TODO: pass through
    // TODO: set 2 Frame and 2 int[] to NULL at the end of compute2 to save some traffic back, but should be small and insignificant
  }

  @Override
  public void compute2() {
    _timings = new double[20];
    long t0 = System.nanoTime();


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/

    SingleThreadRadixOrder.OXHeader leftSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(/*left=*/true, _leftMSB));
    if (leftSortedOXHeader == null) {
      if (_allRight) throw H2O.unimpl();  // TODO pass through _allRight and implement
      tryComplete(); return;
    }


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/

    SingleThreadRadixOrder.OXHeader rightSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(/*left=*/false, _rightMSB));
    //if (_rightMSB==-1) assert _allLeft && rightSortedOXHeader == null; // i.e. it's known nothing on right can join
    if (rightSortedOXHeader == null) {
      if (_allLeft == false) { tryComplete(); return; }
      rightSortedOXHeader = new SingleThreadRadixOrder.OXHeader(0, 0, 0);  // enables general case code to run below without needing new special case code
    }
    _leftBatchSize = leftSortedOXHeader._batchSize;
    _rightBatchSize = rightSortedOXHeader._batchSize;
    _perNodeNumRightRowsToFetch = new long[H2O.CLOUD.size()];
    _perNodeNumLeftRowsToFetch = new long[H2O.CLOUD.size()];

    // get left batches
    _leftKey = new byte[leftSortedOXHeader._nBatch][];
    _leftOrder = new long[leftSortedOXHeader._nBatch][];
    for (int b=0; b<leftSortedOXHeader._nBatch; ++b) {
      Value v = DKV.get(SplitByMSBLocal.getSortedOXbatchKey(/*left=*/true, _leftMSB, b));
      SplitByMSBLocal.OXbatch oxLeft = v.get(); //mem version (obtained from remote) of the Values gets turned into POJO version
      v.freeMem(); //only keep the POJO version of the Value
      _leftKey[b] = oxLeft._x;
      _leftOrder[b] = oxLeft._o;
    }
    _leftN = leftSortedOXHeader._numRows;
    assert _leftN >= 1;

    // get right batches
    _rightKey = new byte[rightSortedOXHeader._nBatch][];
    _rightOrder = new long[rightSortedOXHeader._nBatch][];
    for (int b=0; b<rightSortedOXHeader._nBatch; ++b) {
      Value v = DKV.get(SplitByMSBLocal.getSortedOXbatchKey(/*left=*/false, _rightMSB, b));
      SplitByMSBLocal.OXbatch oxRight = v.get();
      v.freeMem();
      _rightKey[b] = oxRight._x;
      _rightOrder[b] = oxRight._o;
    }
    _rightN = rightSortedOXHeader._numRows;

    _leftKeyNCol = _leftFieldSizes.length;
    _rightKeyNCol = _rightFieldSizes.length;
    _leftKeySize = ArrayUtils.sum(_leftFieldSizes);
    _rightKeySize = ArrayUtils.sum(_rightFieldSizes);
    // System.out.println("_leftKeySize="+_leftKeySize + " _rightKeySize="+_rightKeySize + " _leftN="+_leftN + " _rightN="+_rightN);
    _numJoinCols = Math.min(_leftKeyNCol, _rightKeyNCol);

    // Create fast lookups to go from chunk index to node index of that chunk
    // TODO: must these be created for each and every instance?  Only needed once per node.
    _leftChunkNode = new int[_leftFrame.anyVec().nChunks()];
    _rightChunkNode = new int[_rightFrame.anyVec().nChunks()];
    for (int i=0; i<_leftFrame.anyVec().nChunks(); i++) {
      _leftChunkNode[i] = _leftFrame.anyVec().chunkKey(i).home_node().index();
    }
    for (int i=0; i<_rightFrame.anyVec().nChunks(); i++) {
      _rightChunkNode[i] = _rightFrame.anyVec().chunkKey(i).home_node().index();
    }

    _leftVec = _leftFrame.anyVec();
    _rightVec = _rightFrame.anyVec();

    _timings[0] += (System.nanoTime() - t0) / 1e9;


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/


    // Now calculate which subset of leftMSB and which subset of rightMSB we're joining here
    // by going into the detail of the key values present rather than the extents of the range (the extents themselves may not be present)
    // We see where the right extents occur in the left keys present; and if there is an overlap we find the full extent of
    // the overlap on the left side (nothing less).
    // We only _need_ do this for left outer join otherwise we'd end up with too many no-match left rows.
    // We'll waste allocating the retFirst and retLen vectors though if only a small overlap is needed, so
    // for that reason it's useful to restrict size of retFirst and retLen even for inner join too.

    // Find left and right MSB extents in terms of the key boundaries they represent
    assert 0<=_leftMSB && _leftMSB<=255 && -1<=_rightMSB && _rightMSB<=255;  // _rightMSB==-1 indicates that no right MSB should be looked at
    if (_rightMSB==-1) assert _allLeft;
    long leftMin = (((long)_leftMSB) << _leftShift) + _leftBase[0]-1;  // the first key possible in this bucket
    long leftMax = (((long)_leftMSB+1) << _leftShift) + _leftBase[0] - 2;    // the last key possible in this bucket
    long rightMin = (((long)_rightMSB) << _rightShift) + _rightBase[0]-1;    // if _rightMSB==-1 then the values in rightMin and rightMax here are redundant and not used
    long rightMax = (((long)_rightMSB+1) << _rightShift) + _rightBase[0] - 2;

    _leftFrom =   (_rightMSB==-1 || leftMin>=rightMin || (_allLeft && _rightMSB==0))   ? -1     : bsearchLeft(rightMin, /*retLow*/true);
    long leftTo = (_rightMSB==-1 || leftMax<=rightMax || (_allLeft && _rightMSB==255)) ? _leftN : bsearchLeft(rightMax, /*retLow*/false);
    // The (_allLeft && rightMSB==0) part is to include those keys in that leftMSB just below the right base. They won't be caught by rightMSBs to the left
    // because there are no more rightMSBs below 0. Only when _allLeft do we need to create NA match for them.  They must be created in the same MSB/MSB
    // pair along with the keys that may match the very lowest right keys, because stitching assumes unique MSB/MSB pairs.

    long retSize = leftTo - _leftFrom - 1;   // since leftTo and leftFrom are 1 outside the extremes
    assert retSize >= 0;
    if (retSize==0) { tryComplete(); return; }   // nothing can match, even when allLeft
    _retBatchSize = 268435456;    // 2^31 / 8 since Java arrays are limited to 2^31 bytes

    int retNBatch = (int)((retSize - 1) / _retBatchSize + 1);
    int retLastSize = (int)(retSize - (retNBatch - 1) * _retBatchSize);

    _retFirst = new long[retNBatch][];
    _retLen = new long[retNBatch][];
    int b;
    for (b=0; b<retNBatch-1; b++) {
      _retFirst[b] = MemoryManager.malloc8(_retBatchSize);
      _retLen[b] = MemoryManager.malloc8(_retBatchSize);
    }
    _retFirst[b] = MemoryManager.malloc8(retLastSize);
    _retLen[b] = MemoryManager.malloc8(retLastSize);

    t0 = System.nanoTime();
    bmerge_r(_leftFrom, leftTo, -1, _rightN);   // always look at the whole right bucket.  Even though in types -1 and 1, we know range is outside so nothing should match
                                                // if types -1 and 1 do occur, they only happen for leftMSB 0 and 255, and will quickly resolve to no match in the right bucket via bmerge
    _timings[1] += (System.nanoTime() - t0) / 1e9;

    if (_allLeft) {
      assert ArrayUtils.sum(_perNodeNumLeftRowsToFetch) == retSize;
    } else {
      long tt = 0;
      for (int i=0; i<_retFirst.length; i++)    // i.e. sum(_retFirst>0) in R
        for (int j=0; j<_retFirst[i].length; j++)
          tt += (_retFirst[i][j] > 0) ? 1 : 0;
      assert tt <= retSize;  // TODO: change to tt.privateAssertMethod() containing the loop above to avoid that loop when asserts are off,
                             //       or accumulate the tt inside the merge_r, somehow
      assert ArrayUtils.sum(_perNodeNumLeftRowsToFetch) == tt;
    }

    if (_numRowsInResult > 0) createChunksInDKV();

    // TODO: recheck transients or null out here before returning
    tryComplete();
  }


  //TODO specialize keycmp for cases when no join column contains NA (very very often) and make this totally branch free; i.e. without the two `==0 ? :`
  private int keycmp(byte x[][], long xi, byte y[][], long yi) {
    // Must be passed a left key and a right key to avoid call overhead of extra arguments.
    // Only need left to left for equality only and that's optimized in leftKeyEqual below.
    //long t0 = System.nanoTime();
    //_timings[12] += 1;
    byte xbatch[] = x[(int)(xi / _leftBatchSize)];
    byte ybatch[] = y[(int)(yi / _rightBatchSize)];
    int xoff = (int)(xi % _leftBatchSize) * _leftKeySize;
    int yoff = (int)(yi % _rightBatchSize) * _rightKeySize;
    long xval=0, yval=0;

    // We don't avoid unsafe here because it's unsafe but because we want finer grain compression than 1,2,4 or 8 bytes types. In particular,
    // a range just greater than 4bn can use 5 bytes rather than 8 bytes; a 38% RAM saving over the wire in that possibly common case.
    // Note this is tight and almost branch free.
    int i=0;
    while (i<_numJoinCols && xval==yval) {    // TO DO: pass i in to start at a later key column, when known
      int xlen = _leftFieldSizes[i];
      int ylen = _rightFieldSizes[i];
      xval = xbatch[xoff] & 0xFFL; while (xlen>1) { xval <<= 8; xval |= xbatch[++xoff] & 0xFFL; xlen--; } xoff++;
      yval = ybatch[yoff] & 0xFFL; while (ylen>1) { yval <<= 8; yval |= ybatch[++yoff] & 0xFFL; ylen--; } yoff++;
      xval = xval==0 ? Long.MIN_VALUE : xval-1+_leftBase[i];
      yval = yval==0 ? Long.MIN_VALUE : yval-1+_rightBase[i];
      i++;
    }
    long diff = xval-yval;  // could overflow even in long; e.g. joining to a prevailing NA, or very large gaps O(2^62)
    if (xval>yval) {        // careful not diff>0 here due to overflow
      return( (diff<0 | diff>Integer.MAX_VALUE) ? Integer.MAX_VALUE : (int)diff );
    } else {
      return( (diff>0 | diff<Integer.MIN_VALUE+1) ? Integer.MIN_VALUE+1 : (int)diff);
    }
    // Same return value as strcmp in C. <0 => xi<yi.
    // The magnitude of the difference is used for limiting staleness in a rolling join, capped at Integer.MAX|(MIN+1). roll's type is chosen to be int so staleness can't be requested over int's limit.
  }

  private long bsearchLeft(long x, boolean returnLow) {
    // binary search to the left MSB in the 1st column only
    long low = -1;
    long upp = _leftN;
    while (low < upp - 1) {
      long mid = low + (upp - low) / 2;
      byte keyBatch[] = _leftKey[(int)(mid / _leftBatchSize)];
      int off = (int)(mid % _leftBatchSize) * _leftKeySize;
      int len = _leftFieldSizes[0];
      long val = keyBatch[off] & 0xFFL; while (len>1) { val <<= 8; val |= keyBatch[++off] & 0xFFL; len--; } off++;
      val = val==0 ? Long.MIN_VALUE : val-1+_leftBase[0];
      if (x<val || (x==val && returnLow)) {
        upp = mid;
      } else {
        low = mid;
      }
    }
    return returnLow ? low : upp;
  }

  private boolean leftKeyEqual(byte x[][], long xi, long yi) {
    // Must be passed two leftKeys only.
    // Optimized special case for the two calling points; see usages in bmerge_r below.
    byte xbatch[] = x[(int)(xi / _leftBatchSize)];
    byte ybatch[] = x[(int)(yi / _leftBatchSize)];
    int xoff = (int)(xi % _leftBatchSize) * _leftKeySize;
    int yoff = (int)(yi % _leftBatchSize) * _leftKeySize;
    int i=0;
    while (i<_leftKeySize && xbatch[xoff++] == ybatch[yoff++]) i++;
    return(i==_leftKeySize);
  }

  private void bmerge_r(long lLowIn, long lUppIn, long rLowIn, long rUppIn) {
    // TO DO: parallel each of the 256 bins
    long lLow = lLowIn, lUpp = lUppIn, rLow = rLowIn, rUpp = rUppIn;
    long mid, tmpLow, tmpUpp;
    long lr = lLow + (lUpp - lLow) / 2;   // i.e. (lLow+lUpp)/2 but being robust to one day in the future someone somewhere overflowing long; e.g. 32 exabytes of 1-column ints
    while (rLow < rUpp - 1) {
      mid = rLow + (rUpp - rLow) / 2;
      int cmp = keycmp(_leftKey, lr, _rightKey, mid);  // -1, 0 or 1, like strcmp
      if (cmp < 0) {
        rUpp = mid;
      } else if (cmp > 0) {
        rLow = mid;
      } else { // rKey == lKey including NA == NA
        // branch mid to find start and end of this group in this column
        // TO DO?: not if mult=first|last and col<ncol-1
        tmpLow = mid;
        tmpUpp = mid;
        while (tmpLow < rUpp - 1) {
          mid = tmpLow + (rUpp - tmpLow) / 2;
          if (keycmp(_leftKey, lr, _rightKey, mid) == 0) tmpLow = mid;
          else rUpp = mid;
        }
        while (rLow < tmpUpp - 1) {
          mid = rLow + (tmpUpp - rLow) / 2;
          if (keycmp(_leftKey, lr, _rightKey, mid) == 0) tmpUpp = mid;
          else rLow = mid;
        }
        break;
      }
    }
    // rLow and rUpp now surround the group in the right table.

    // The left table key may (unusually, and not recommended, but sometimes needed) be duplicated.
    // Linear search outwards from left row. Most commonly, the first test shows this left key is unique.
    // This saves i) re-finding the matching rows in the right for all the dup'd left and ii) recursive bounds logic gets awkward if other left rows can find the same right rows
    // Related to 'allow.cartesian' in data.table.
    // TO DO:  if index stores attribute that it is unique then we don't need this step. However, each of these while()s would run at most once in that case, which may not be worth optimizing.
    tmpLow = lr + 1;
    while (tmpLow<lUpp && leftKeyEqual(_leftKey, tmpLow, lr)) tmpLow++;  // TODO: these while's could be rolled up inside leftKeyEqual saving call overhead
    lUpp = tmpLow;
    tmpUpp = lr - 1;
    while (tmpUpp>lLow && leftKeyEqual(_leftKey, tmpUpp, lr)) tmpUpp--;
    lLow = tmpUpp;
    // lLow and lUpp now surround the group in the left table.  If left key is unique then lLow==lr-1 and lUpp==lr+1.
    assert lUpp - lLow >= 2;

    long len = rUpp - rLow - 1;  // if value found, rLow and rUpp surround it, unlike standard binary search where rLow falls on it
    // TO DO - we don't need loop here :)  Why does perNodeNumRightRowsToFetch increase so much?
    if (len > 0 || _allLeft) {
      long t0 = System.nanoTime();
      if (len > 1) _oneToManyMatch = true;
      _numRowsInResult += Math.max(1,len) * (lUpp-lLow-1);   // 1 for NA row when _allLeft
      for (long j = lLow + 1; j < lUpp; j++) {   // usually iterates once only for j=lr, but more than once if there are dup keys in left table
        // may be a range of left dup'd join-col values, but we need to fetch each one since the left non-join columns are likely not dup'd and may be the reason for the cartesian join
        long t00 = System.nanoTime();
        int jb = (int)(j/_leftBatchSize);   // TODO could loop through batches rather than / and % wastefully
        int jo = (int)(j%_leftBatchSize);
        long globalRowNumber = _leftOrder[jb][jo];
        _timings[17] += (System.nanoTime() - t00)/1e9;
        t00 = System.nanoTime();
        int chkIdx = _leftVec.elem2ChunkIdx(globalRowNumber); //binary search in espc
        _timings[15] += (System.nanoTime() - t00)/1e9;
        _perNodeNumLeftRowsToFetch[_leftChunkNode[chkIdx]]++;  // the key is the same within this left dup range, but still need to fetch left non-join columns
        if (len==0) continue;  // _allLeft must be true if len==0

        // TODO:  initial MSB splits should split down to small enough chunk size - but would that require more passes and if so, how long?  Code simplification benefits would be welcome!
        long outLoc = j - (_leftFrom + 1);   // outOffset is 0 here in the standard scaling up high cardinality test
        jb = (int)(outLoc/_retBatchSize);  // outBatchSize can be different, and larger since known to be 8 bytes per item, both retFirst and retLen.  (Allowing 8 byte here seems wasteful, actually.)
        jo = (int)(outLoc%_retBatchSize);  // TODO - take outside the loop.  However when we go deep-msb, this'll go away.

        _retFirst[jb][jo] = rLow + 2;  // rLow surrounds row, so +1.  Then another +1 for 1-based row-number. 0 (default) means nomatch and saves extra set to -1 for no match.  Could be significant in large edge cases by not needing to write at all to _retFirst if it has no matches.
        _retLen[jb][jo] = len;
        //StringBuilder sb = new StringBuilder();
        //sb.append("Left row " + _leftOrder[jb][jo] + " matches to " + _retLen[jb][jo] + " right rows: ");
      }
      // if we have dup'd left row, we only need to fetch the right rows once for the first dup.  Those should then be recycled locally later.
      for (long i=0; i<len; i++) {
        long loc = rLow+1+i;
        //sb.append(_rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)] + " ");
        long t00 = System.nanoTime();
        long globalRowNumber = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];  // TODO could loop through batches rather than / and % wastefully
        _timings[18] += (System.nanoTime() - t00)/1e9;
        t00 = System.nanoTime();
        int chkIdx = _rightVec.elem2ChunkIdx(globalRowNumber); //binary search in espc
        _timings[16] += (System.nanoTime() - t00)/1e9;
        _perNodeNumRightRowsToFetch[_rightChunkNode[chkIdx]]++;  // just count the number per node. So we can allocate arrays precisely up front, and also to return early to use in case of memory errors or other distribution problems
      }
      _timings[14] += (System.nanoTime() - t0)/1e9;
    }
    // TO DO: check assumption that retFirst and retLength are initialized to 0, for case of no match
    // Now branch (and TO DO in parallel) to merge below and merge above
    if (lLow > lLowIn && (rLow > rLowIn || _allLeft)) // '|| _allLeft' is needed here in H2O (but not data.table) for the _perNodeNumLeftRowsToFetch above to populate and pass the assert near the end of the compute2() above.
      bmerge_r(lLowIn, lLow + 1, rLowIn, rLow+1);
    if (lUpp < lUppIn && (rUpp < rUppIn || _allLeft))
      bmerge_r(lUpp-1, lUppIn, rUpp-1, rUppIn);

    // We don't feel tempted to reduce the global _ansN here and make a global frame,
    // since we want to process each MSB l/r combo individually without allocating them all.
    // Since recursive, no more code should be here (it would run too much)
  }

  private void createChunksInDKV() {

    // Collect all matches
    // Create the final frame (part) for this MSB combination
    // Cannot use a List<Long> as that's restricted to 2Bn items and also isn't an Iced datatype
    long t0 = System.nanoTime();
    long perNodeRightRows[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeRightLoc[] = new long[H2O.CLOUD.size()];

    long perNodeLeftRows[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeLeftLoc[] = new long[H2O.CLOUD.size()];


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }
    */


    // Allocate memory to split this MSB combn's left and right matching rows into contiguous batches sent to the nodes they reside on
    int batchSize = 256*1024*1024 / 8;  // 256GB DKV limit / sizeof(long)
    //int thisNode = H2O.SELF.index();
    for (int i = 0; i < H2O.CLOUD.size(); i++) {
      if (_perNodeNumRightRowsToFetch[i] > 0) {
        int nbatch = (int) ((_perNodeNumRightRowsToFetch[i] - 1) / batchSize + 1);  // TODO: wrap in class to avoid this boiler plate
        int lastSize = (int) (_perNodeNumRightRowsToFetch[i] - (nbatch - 1) * batchSize);
        // System.out.println("Sending " +_perNodeNumRightRowsToFetch[i]+ " row requests to node " +i+ " in " +nbatch+ " batches from node " +thisNode+ " for rightMSB " +_rightMSB);
        assert nbatch >= 1;
        assert lastSize > 0;
        perNodeRightRows[i] = new long[nbatch][];
        int b;
        for (b = 0; b < nbatch - 1; b++) perNodeRightRows[i][b] = MemoryManager.malloc8(batchSize);
        perNodeRightRows[i][b] = MemoryManager.malloc8(lastSize);
      }
      if (_perNodeNumLeftRowsToFetch[i] > 0) {
        int nbatch = (int) ((_perNodeNumLeftRowsToFetch[i] - 1) / batchSize + 1);  // TODO: wrap in class to avoid this boiler plate
        int lastSize = (int) (_perNodeNumLeftRowsToFetch[i] - (nbatch - 1) * batchSize);
        // System.out.println("Sending " +_perNodeNumLeftRowsToFetch[i]+ " row requests to node " +i+ " in " +nbatch+ " batches from node " +thisNode+ " for leftMSB " + _leftMSB);
        assert nbatch >= 1;
        assert lastSize > 0;
        perNodeLeftRows[i] = new long[nbatch][];
        int b;
        for (b = 0; b < nbatch - 1; b++) perNodeLeftRows[i][b] = MemoryManager.malloc8(batchSize);
        perNodeLeftRows[i][b] = MemoryManager.malloc8(lastSize);
      }
    }
    _timings[2] += (System.nanoTime() - t0) / 1e9;
    t0 = System.nanoTime();


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/



    // Loop over _retFirst and _retLen and populate the batched requests for each node helper
    // _retFirst and _retLen are the same shape
    long prevf = -1, prevl = -1;
    long resultLoc=0;  // sweep upwards through the final result, filling it in
    long leftLoc=_leftFrom;  // sweep through left table along the sorted row locations.  // TODO: hop back to original order here for [] syntax.
    for (int jb=0; jb<_retFirst.length; ++jb) {              // jb = j batch
      for (int jo=0; jo<_retFirst[jb].length; ++jo) {        // jo = j offset
        leftLoc++;  // to save jb*_retFirst[0].length + jo;
        long f = _retFirst[jb][jo];  // TODO: take _retFirst[jb] outside inner loop
        long l = _retLen[jb][jo];
        if (f==0) {
          // left row matches to no right row
          assert l == 0;  // doesn't have to be 0 (could be 1 already if allLeft==true) but currently it should be, so check it
          if (!_allLeft) continue;
          // now insert the left row once and NA for the right columns i.e. left outer join
        }
        { // new scope so 'row' can be declared in the for() loop below and registerized (otherwise 'already defined in this scope' in that scope)
          // Fetch the left rows and mark the contiguous from-ranges each left row should be recycled over
          // TODO: when single node, not needed
          long row = _leftOrder[(int)(leftLoc / _leftBatchSize)][(int)(leftLoc % _leftBatchSize)];  // TODO could loop through batches rather than / and % wastefully
          int chkIdx = _leftVec.elem2ChunkIdx(row); //binary search in espc
          int ni = _leftChunkNode[chkIdx];
          long pnl = perNodeLeftLoc[ni]++;   // pnl = per node location
          perNodeLeftRows[ni][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = row;  // ask that node for global row number row
        }
        if (f==0) { resultLoc++; continue; }
        assert l > 0;
        if (prevf == f && prevl == l) continue;  // don't re-fetch the same matching rows (cartesian). We'll repeat them locally later.
        prevf = f; prevl = l;
        for (int r=0; r<l; r++) {
          long loc = f+r-1;  // -1 because these are 0-based where 0 means no-match and 1 refers to the first row
          long row = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];   // TODO: could take / and % outside loop in cases where it doesn't span a batch boundary
          // find the owning node for the row, using local operations here
          int chkIdx = _rightVec.elem2ChunkIdx(row); //binary search in espc
          int ni = _rightChunkNode[chkIdx];
          long pnl = perNodeRightLoc[ni]++;   // pnl = per node location.   // TODO Split to an if() and batch and offset separately
          perNodeRightRows[ni][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = row;  // ask that node for global row number row
        }
      }
    }
    for (int i = 0; i < H2O.CLOUD.size(); i++) {
      // TODO assert that perNodeRite and Left are exactly equal to the number expected and allocated.
      perNodeLeftLoc[i] = 0;  // clear for reuse below
      perNodeRightLoc[i] = 0;
    }
    _timings[3] += (System.nanoTime() - t0) / 1e9;
    t0 = System.nanoTime();

    // Create the chunks for the final frame from this MSB pair.
    batchSize = 256*1024*1024 / 16;  // number of rows per chunk to fit in 256GB DKV limit.   16 bytes for each UUID (biggest type). Enum will be long (8). TODO: How is non-Enum 'string' handled by H2O?
    int nbatch = (int) ((_numRowsInResult-1)/batchSize +1);  // TODO: wrap in class to avoid this boiler plate
    int lastSize = (int) (_numRowsInResult - (nbatch-1)*batchSize);
    assert nbatch >= 1;
    assert lastSize > 0;
    _chunkSizes = new int[nbatch];
    int _numLeftCols = _leftFrame.numCols();
    int _numColsInResult = _leftFrame.numCols() + _rightFrame.numCols() - _numJoinCols;
    double[][][] frameLikeChunks = new double[_numColsInResult][nbatch][]; //TODO: compression via int types
    for (int col=0; col<_numColsInResult; col++) {
      int b;
      for (b = 0; b < nbatch - 1; b++) {
        frameLikeChunks[col][b] = MemoryManager.malloc8d(batchSize);
        Arrays.fill(frameLikeChunks[col][b], Double.NaN);   // NA by default to save filling with NA for nomatches when allLeft
        _chunkSizes[b] = batchSize;
      }
      frameLikeChunks[col][b] = MemoryManager.malloc8d(lastSize);
      Arrays.fill(frameLikeChunks[col][b], Double.NaN);
      _chunkSizes[b] = lastSize;
    }
    _timings[4] += (System.nanoTime() - t0) / 1e9;
    t0 = System.nanoTime();


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/



    RPC<GetRawRemoteRows> grrrsRiteRPC[][] = new RPC[H2O.CLOUD.size()][];
    RPC<GetRawRemoteRows> grrrsLeftRPC[][] = new RPC[H2O.CLOUD.size()][];
    GetRawRemoteRows grrrsLeft[][] = new GetRawRemoteRows[H2O.CLOUD.size()][];
    GetRawRemoteRows grrrsRite[][] = new GetRawRemoteRows[H2O.CLOUD.size()][];
    for (H2ONode node : H2O.CLOUD._memary) {
      int ni = node.index();
      int bUppRite = perNodeRightRows[ni] == null ? 0 : perNodeRightRows[ni].length;
      int bUppLeft =  perNodeLeftRows[ni] == null ? 0 :  perNodeLeftRows[ni].length;
      grrrsRiteRPC[ni] = new RPC[bUppRite];
      grrrsLeftRPC[ni] = new RPC[bUppLeft];
      grrrsRite[ni] = new GetRawRemoteRows[bUppRite];
      grrrsLeft[ni] = new GetRawRemoteRows[bUppLeft];
      for (int b = 0; b < bUppRite; b++) {
        // Arrays.sort(perNodeRightRows[ni][b]);  Simple quick test of fetching in monotonic order. Doesn't seem to help so far. TODO try again now with better surrounding method
        grrrsRiteRPC[ni][b] = new RPC<>(node, new GetRawRemoteRows(_rightFrame, perNodeRightRows[ni][b])).call();
      }
      for (int b = 0; b < bUppLeft; b++) {
        // Arrays.sort(perNodeLeftRows[ni][b]);
        grrrsLeftRPC[ni][b] = new RPC<>(node, new GetRawRemoteRows(_leftFrame, perNodeLeftRows[ni][b])).call();
      }
    }
    for (H2ONode node : H2O.CLOUD._memary) {
      // TODO: just send and wait for first batch on each node and then .get() next batch as needed.
      int ni = node.index();
      int bUppRite = perNodeRightRows[ni] == null ? 0 : perNodeRightRows[ni].length;
      for (int b = 0; b < bUppRite; b++) {
        _timings[5] += (grrrsRite[ni][b] = grrrsRiteRPC[ni][b].get()).timeTaken;
      }
      int bUppLeft = perNodeLeftRows[ni] == null ? 0 :  perNodeLeftRows[ni].length;
      for (int b = 0; b < bUppLeft; b++) {
        _timings[5] += (grrrsLeft[ni][b] = grrrsLeftRPC[ni][b].get()).timeTaken;
      }
    }
    _timings[6] += (System.nanoTime() - t0) / 1e9;   // all this time is expected to be in [5]
    t0 = System.nanoTime();
    grrrsRiteRPC = null;
    grrrsLeftRPC = null;

    // Now loop through _retFirst and _retLen and populate
    resultLoc=0;   // sweep upwards through the final result, filling it in
    leftLoc=_leftFrom;   // sweep through left table along the sorted row locations.  // TODO: hop back to original order here for [] syntax.
    prevf = -1; prevl = -1;
    for (int jb=0; jb<_retFirst.length; ++jb) {              // jb = j batch
      for (int jo=0; jo<_retFirst[jb].length; ++jo) {        // jo = j offset
        leftLoc++;  // to save jb*_retFirst[0].length + jo;
        long f = _retFirst[jb][jo];  // TODO: take _retFirst[jb] outside inner loop
        long l = _retLen[jb][jo];
        if (f==0 && !_allLeft) continue;  // f==0 => left row matches to no right row
        // else insert the left row once and NA for the right columns i.e. left outer join

        // Fetch the left rows and recycle it if more than 1 row in the right table is matched to
        long row = _leftOrder[(int)(leftLoc / _leftBatchSize)][(int)(leftLoc % _leftBatchSize)];  // TODO could loop through batches rather than / and % wastefully
        // TODO should leftOrder and retFirst/retLen have the same batch size to make this easier?
        // TODO Can we not just loop through _leftOrder only? Why jb and jo too through
        int chkIdx = _leftVec.elem2ChunkIdx(row); //binary search in espc
        int ni = _leftChunkNode[chkIdx];
        long pnl = perNodeLeftLoc[ni]++;   // pnl = per node location.  TODO: batch increment this rather than
        int b = (int)(pnl / batchSize);
        int o = (int)(pnl % batchSize);
        double[][] chks = grrrsLeft[ni][b]._chk;

        for (int rep = 0; rep < Math.max(l,1); rep++) {
          long a = resultLoc + rep;
          int whichChunk = (int) (a / batchSize);  // TODO: loop into batches to save / and % for each repeat and still cater for crossing multiple batch boundaries
          int offset = (int) (a % batchSize);

          for (int col=0; col<chks.length; col++) {
            frameLikeChunks[col][whichChunk][offset] = chks[col][o];  // colForBatch.atd(row); TODO: this only works for numeric columns (not for date, UUID, strings, etc.)
          }
        }
        if (f==0) { resultLoc++; continue; } // no match so just one row (NA for right table) to advance over
        assert l > 0;
        if (prevf == f && prevl == l) {
          // ** just copy from previous batch in the result (populated by for() below). Contiguous easy in-cache copy (other than batches).
          for (int r=0; r<l; r++) {
            int toChunk = (int) (resultLoc / batchSize);  // TODO: loop into batches to save / and % for each repeat and still cater for crossing multiple batch boundaries
            int toOffset = (int) (resultLoc % batchSize);
            int fromChunk = (int) ((resultLoc - l) / batchSize);
            int fromOffset = (int) ((resultLoc - l) % batchSize);
            for (int col=0; col<_numColsInResult-_numLeftCols; col++) {
              frameLikeChunks[_numLeftCols + col][toChunk][toOffset] = frameLikeChunks[_numLeftCols + col][fromChunk][fromOffset];
            }
            resultLoc++;
          }
          continue;
        }
        prevf = f;
        prevl = l;
        for (int r=0; r<l; r++) {
          int whichChunk = (int) (resultLoc / batchSize);  // TODO: loop into batches to save / and % for each repeat and still cater for crossing multiple batch boundaries
          int offset = (int) (resultLoc % batchSize);
          long loc = f+r-1;  // -1 because these are 0-based where 0 means no-match and 1 refers to the first row
          row = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];   // TODO: could take / and % outside loop in cases where it doesn't span a batch boundary
          // find the owning node for the row, using local operations here
          chkIdx = _rightVec.elem2ChunkIdx(row); //binary search in espc
          ni = _rightChunkNode[chkIdx];
          pnl = perNodeRightLoc[ni]++;   // pnl = per node location.   // TODO Split to an if() and batch and offset separately
          chks = grrrsRite[ni][(int)(pnl / batchSize)]._chk;
          o = (int)(pnl % batchSize);
          for (int col=0; col<_numColsInResult-_numLeftCols; col++) {
            frameLikeChunks[_numLeftCols + col][whichChunk][offset] = chks[_numJoinCols + col][o];  // colForBatch.atd(row); TODO: this only works for numeric columns (not for date, UUID, strings, etc.)
          }
          resultLoc++;
        }
      }
    }
    _timings[10] += (System.nanoTime() - t0) / 1e9;
    t0 = System.nanoTime();
    grrrsLeft = null;  // remove now to free memory. We moved all these into frameLikeChunks now.
    grrrsRite = null;


    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/



    // compress all chunks and store them
    Futures fs = new Futures();
    for (int col=0; col<_numColsInResult; col++) {
      for (int b = 0; b < nbatch; b++) {
        Chunk ck = new NewChunk(frameLikeChunks[col][b]).compress();
        DKV.put(getKeyForMSBComboPerCol(/*_leftFrame, _rightFrame,*/ _leftMSB, _rightMSB, col, b), ck, fs, true);
        frameLikeChunks[col][b]=null; //free mem as early as possible (it's now in the store)
      }
    }
    fs.blockForPending();
    _timings[11] += (System.nanoTime() - t0) / 1e9;

    /*for (int s=0; s<1; s++) {
      try { Thread.sleep(1000); } catch(InterruptedException ex) { Thread.currentThread().interrupt(); }
      System.gc();
    }*/


  }

  static Key getKeyForMSBComboPerCol(/*Frame leftFrame, Frame rightFrame,*/ int leftMSB, int rightMSB, int col /*final table*/, int batch) {
    return Key.make("__binary_merge__Chunk_for_col" + col + "_batch" + batch
                    // + rightFrame._key.toString() + "_joined_with" + leftFrame._key.toString()
                    + "_leftMSB" + leftMSB + "_rightMSB" + rightMSB,
            (byte) 1, Key.HIDDEN_USER_KEY, false, SplitByMSBLocal.ownerOfMSB(rightMSB)
    ); //TODO home locally
  }

  class GetRawRemoteRows extends DTask<GetRawRemoteRows> {
    double[/*col*/][] _chk; //null on the way to remote node, non-null on the way back
    long[/*rows*/] _rows; //which rows to fetch from remote node, non-null on the way to remote, null on the way back
    double timeTaken;
    Frame _fr;
    GetRawRemoteRows(Frame fr, long[] rows) {
      _rows = rows;
      _fr = fr;
    }

    @Override
    public void compute2() {
      assert(_rows!=null);
      assert(_chk ==null);
      long t0 = System.nanoTime();
      // System.out.print("Allocating _chk with " + _fr.numCols() +" by " + _rows.length + "...");
      _chk  = MemoryManager.malloc8d(_fr.numCols(),_rows.length);  // TODO: should this be transposed in memory?
      // System.out.println("done");
      int cidx[] = MemoryManager.malloc4(_rows.length);
      int offset[] = MemoryManager.malloc4(_rows.length);
      Vec anyVec = _fr.anyVec();
      for (int row=0; row<_rows.length; row++) {
        cidx[row] = anyVec.elem2ChunkIdx(_rows[row]);  // binary search of espc array.  TODO: sort input row numbers to avoid
        offset[row] = (int)(_rows[row] - anyVec.espc()[cidx[row]]);
      }
      Chunk c[] = new Chunk[anyVec.nChunks()];
      for (int col=0; col<_fr.numCols(); col++) {
        Vec v = _fr.vec(col);
        for (int i=0; i<c.length; i++) c[i] = v.chunkKey(i).home() ? v.chunkForChunkIdx(i) : null;
        for (int row=0; row<_rows.length; row++) {
          _chk[col][row] = c[cidx[row]].atd(offset[row]);
        }
      }

      // tell remote node to fill up Chunk[/*batch*/][/*rows*/]
      // perNodeRows[node] has perNodeRows[node].length batches of row numbers to fetch
      _rows=null;
      assert(_chk !=null);

      timeTaken = (System.nanoTime() - t0) / 1e9;

      tryComplete();
    }
  }
}
