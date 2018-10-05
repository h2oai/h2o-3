package water.rapids;

// Since we have a single key field in H2O (different to data.table), bmerge() becomes a lot simpler (no
// need for recursion through join columns) with a downside of transfer-cost should we not need all the key.

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.ArrayUtils;
import water.util.Log;

import java.math.BigInteger;
import java.util.Arrays;

import static water.rapids.SingleThreadRadixOrder.getSortedOXHeaderKey;

class BinaryMerge extends DTask<BinaryMerge> {
  long _numRowsInResult=0;  // returned to caller, so not transient
  int _chunkSizes[]; // TODO:  only _chunkSizes.length is needed by caller, so return that length only
  double _timings[];

  private transient long _ret1st[/*n2GB*/][];  // The row number of the first right table's index key that matches
  private transient long _retLen[/*n2GB*/][];   // How many rows does it match to?

  final FFSB _leftSB, _riteSB;
  private transient KeyOrder _leftKO, _riteKO;

  private final int _numJoinCols;
  private transient long _leftFrom;
  private transient int _retBatchSize;

  private final boolean _allLeft, _allRight;
  private boolean[] _stringCols;

  // does any left row match to more than 1 right row?  If not, can allocate
  // and loop more efficiently, and mark the resulting key'd frame with a
  // 'unique' index.  //   TODO: implement
  private transient boolean _oneToManyMatch = false;

  // Data which is duplicated left and rite, but only one copy is needed
  // per-map.  This data is made in the constructor and shallow-copy shared
  // around the cluster.
  static class FFSB extends Iced<FFSB> {
    private final Frame _frame;
    private final Vec _vec;
    // fast lookups to save repeated calls to node.index() which calls
    // binarysearch within it.
    private final int _chunkNode[]; // Chunk homenode index
    final int _msb;
    private final int _shift;
    private final BigInteger _base[]; // the col.min() of each column in the key
    private final int _fieldSizes[]; // the widths of each column in the key
    private final int _keySize; // the total width in bytes of the key, sum of field sizes

    FFSB( Frame frame, int msb, int shift, int fieldSizes[], BigInteger base[]) {
      assert -1<=msb && msb<=255; // left ranges from 0 to 255, right from -1 to 255
      _frame = frame;
      _msb = msb;
      _shift = shift;
      _fieldSizes = fieldSizes;
      _keySize = ArrayUtils.sum(fieldSizes);
      _base = base;
      // Create fast lookups to go from chunk index to node index of that chunk
      Vec vec = _vec = frame.anyVec();
      _chunkNode = vec==null ? null : new int[vec.nChunks()];
      if( vec == null ) return; // Zero-columns for Sort
      for( int i=0; i<_chunkNode.length; i++ )
        _chunkNode[i] = vec.chunkKey(i).home_node().index();
    }

 //   long min() { return (((long)_msb  ) << _shift) + _base[0]-1; } // the first key possible in this bucket
 //   long max() { return (((long)_msb+1) << _shift) + _base[0]-2; } // the last  key possible in this bucket

    long min() {
      return BigInteger.valueOf(((long)_msb) << _shift).add(_base[0].subtract(BigInteger.ONE)).longValue();
    }
    long max() {
      return BigInteger.valueOf(((long)_msb+1) << _shift).add(_base[0].subtract(BigInteger.ONE).subtract(BigInteger.ONE)).longValue();
    }
  }

  // In X[Y], 'left'=i and 'right'=x
  BinaryMerge(FFSB leftSB, FFSB riteSB, boolean allLeft) {
    assert riteSB._msb!=-1 || allLeft;
    _leftSB = leftSB;
    _riteSB = riteSB;
    // the number of columns in the key i.e. length of _leftFieldSizes and _riteSB._fieldSizes
    _numJoinCols = Math.min(_leftSB._fieldSizes.length, _riteSB._fieldSizes.length);
    _allLeft = allLeft;
    _allRight = false;  // TODO: pass through
    int columnsInResult = (_leftSB._frame == null?0:_leftSB._frame.numCols()) +
            (_riteSB._frame == null?0:_riteSB._frame.numCols())-_numJoinCols;
    _stringCols = new boolean[columnsInResult];
    // check left frame first
    if (_leftSB._frame!=null) {
      for (int col = _numJoinCols; col < _leftSB._frame.numCols(); col++) {
        if (_leftSB._frame.vec(col).isString())
          _stringCols[col] = true;
      }
    }
    // check right frame next
    if (_riteSB._frame != null) {
      int colOffset = _leftSB._frame==null?0:_leftSB._frame.numCols()-_numJoinCols;
      for (int col = _numJoinCols; col < _riteSB._frame.numCols(); col++) {
        if (_riteSB._frame.vec(col).isString())
          _stringCols[col + colOffset] = true;
      }
    }
  }


  @Override
  public void compute2() {
    _timings = new double[20];
    long t0 = System.nanoTime();

    SingleThreadRadixOrder.OXHeader leftSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(/*left=*/true, _leftSB._msb));
    if (leftSortedOXHeader == null) {
      if( !_allRight ) { tryComplete(); return; }
      throw H2O.unimpl();  // TODO pass through _allRight and implement
    }
    _leftKO = new KeyOrder(leftSortedOXHeader);

    SingleThreadRadixOrder.OXHeader rightSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(/*left=*/false, _riteSB._msb));
    //if (_riteSB._msb==-1) assert _allLeft && rightSortedOXHeader == null; // i.e. it's known nothing on right can join
    if (rightSortedOXHeader == null) {
      if( !_allLeft ) { tryComplete(); return; }
      // enables general case code to run below without needing new special case code
      rightSortedOXHeader = new SingleThreadRadixOrder.OXHeader(0, 0, 0);  
    }
    _riteKO = new KeyOrder(rightSortedOXHeader);

    // get left batches
    _leftKO.initKeyOrder(_leftSB._msb,/*left=*/true);
    final long leftN = leftSortedOXHeader._numRows;
    assert leftN >= 1;

    // get right batches
    _riteKO.initKeyOrder(_riteSB._msb, /*left=*/false);
    final long rightN = rightSortedOXHeader._numRows;
    
    _timings[0] += (System.nanoTime() - t0) / 1e9;


    // Now calculate which subset of leftMSB and which subset of rightMSB we're
    // joining here by going into the detail of the key values present rather
    // than the extents of the range (the extents themselves may not be
    // present).

    // We see where the right extents occur in the left keys present; and if
    // there is an overlap we find the full extent of the overlap on the left
    // side (nothing less).

    // We only _need_ do this for left outer join otherwise we'd end up with
    // too many no-match left rows.

    // We'll waste allocating the retFirst and retLen vectors though if only a
    // small overlap is needed, so for that reason it's useful to restrict size
    // of retFirst and retLen even for inner join too.

    // Find left and right MSB extents in terms of the key boundaries they represent
    // _riteSB._msb==-1 indicates that no right MSB should be looked at
    final long leftMin = _leftSB.min();  // the first key possible in this bucket
    final long leftMax = _leftSB.max();  // the last  key possible in this bucket
    // if _riteSB._msb==-1 then the values in riteMin and riteMax here are redundant and not used
    final long riteMin = _riteSB._msb==-1 ? -1 : _riteSB.min();  // the first key possible in this bucket
    final long riteMax = _riteSB._msb==-1 ? -1 : _riteSB.max();  // the last  key possible in this bucket

    _leftFrom =   (_riteSB._msb==-1 || leftMin>=riteMin || (_allLeft && _riteSB._msb==0  )) ? -1    : bsearchLeft(riteMin, /*retLow*/true , leftN);
    long leftTo = (_riteSB._msb==-1 || leftMax<=riteMax || (_allLeft && _riteSB._msb==255)) ? leftN : bsearchLeft(riteMax, /*retLow*/false, leftN);
    // The (_allLeft && rightMSB==0) part is to include those keys in that
    // leftMSB just below the right base.  They won't be caught by rightMSBs to
    // the left because there are no more rightMSBs below 0.  Only when
    // _allLeft do we need to create NA match for them.  They must be created
    // in the same MSB/MSB pair along with the keys that may match the very
    // lowest right keys, because stitching assumes unique MSB/MSB pairs.

    long retSize = leftTo - _leftFrom - 1;   // since leftTo and leftFrom are 1 outside the extremes
    assert retSize >= 0;
    if (retSize==0) { tryComplete(); return; } // nothing can match, even when allLeft
    _retBatchSize = 268435456;    // 2^31 / 8 since Java arrays are limited to 2^31 bytes
    int retNBatch = (int)((retSize - 1) / _retBatchSize + 1);
    int retLastSize = (int)(retSize - (retNBatch - 1) * _retBatchSize);

    _ret1st = new long[retNBatch][];
    _retLen = new long[retNBatch][];
    for( int b=0; b<retNBatch; b++) {
      _ret1st[b] = MemoryManager.malloc8(b==retNBatch-1 ? retLastSize : _retBatchSize);
      _retLen[b] = MemoryManager.malloc8(b==retNBatch-1 ? retLastSize : _retBatchSize);
    }

    // always look at the whole right bucket.  Even though in types -1 and 1,
    // we know range is outside so nothing should match.  if types -1 and 1 do
    // occur, they only happen for leftMSB 0 and 255, and will quickly resolve
    // to no match in the right bucket via bmerge
    t0 = System.nanoTime();
    bmerge_r(_leftFrom, leftTo, -1, rightN);
    _timings[1] += (System.nanoTime() - t0) / 1e9;

    if (_allLeft) {
      assert _leftKO.numRowsToFetch() == retSize;
    } else {
      long tt = 0;
      for( long[] retFirstx : _ret1st )    // i.e. sum(_ret1st>0) in R
        for( long rF : retFirstx )
          tt += (rF > 0) ? 1 : 0;
      // TODO: change to tt.privateAssertMethod() containing the loop above to
      //       avoid that loop when asserts are off, or accumulate the tt
      //       inside the merge_r, somehow
      assert tt <= retSize;  
      assert _leftKO.numRowsToFetch() == tt;
    }

    if (_numRowsInResult > 0) createChunksInDKV();

    // TODO: set 2 Frame and 2 int[] to NULL at the end of compute2 to save
    // some traffic back, but should be small and insignificant
    // TODO: recheck transients or null out here before returning
    tryComplete();
  }

  // Holder for Key & Order info
  private static class KeyOrder {
    private final transient long _batchSize;
    private final transient byte _key  [/*n2GB*/][/*i mod 2GB * _keySize*/];
    private final transient long _order[/*n2GB*/][/*i mod 2GB * _keySize*/];
    private final transient long _perNodeNumRowsToFetch[];

    KeyOrder( SingleThreadRadixOrder.OXHeader sortedOXHeader ) {
      _batchSize = sortedOXHeader._batchSize;
      final int nBatch = sortedOXHeader._nBatch;
      _key   = new byte[nBatch][];
      _order = new long[nBatch][];
      _perNodeNumRowsToFetch = new long[H2O.CLOUD.size()];
    }

    void initKeyOrder( int msb, boolean isLeft ) {
      for( int b=0; b<_key.length; b++ ) {
        Value v = DKV.get(SplitByMSBLocal.getSortedOXbatchKey(isLeft, msb, b));
        SplitByMSBLocal.OXbatch ox = v.get(); //mem version (obtained from remote) of the Values gets turned into POJO version
        v.freeMem(); //only keep the POJO version of the Value
        _key  [b] = ox._x;
        _order[b] = ox._o;
      }
    }
    long numRowsToFetch() { return ArrayUtils.sum(_perNodeNumRowsToFetch); }
    // Do a mod/div long _order array lookup
    long at8order( long idx ) { return _order[(int)(idx / _batchSize)][(int)(idx % _batchSize)]; }

    long[][] fillPerNodeRows( int i ) {
      final int batchSizeLong = 256*1024*1024 / 16;  // 256GB DKV limit / sizeof(UUID)
      if( _perNodeNumRowsToFetch[i] <= 0 ) return null;
      int nbatch  = (int) ((_perNodeNumRowsToFetch[i] - 1) / batchSizeLong + 1);  // TODO: wrap in class to avoid this boiler plate
      assert nbatch >= 1;
      int lastSize = (int) (_perNodeNumRowsToFetch[i] - (nbatch - 1) * batchSizeLong);
      assert lastSize > 0;
      long[][] res = new long[nbatch][];
      for( int b = 0; b < nbatch; b++ )
        res[b] = MemoryManager.malloc8(b==nbatch-1 ? lastSize : batchSizeLong);
      return res;
    }
  }


  // TODO: specialize keycmp for cases when no join column contains NA (very
  // very often) and make this totally branch free; i.e. without the two `==0 ? :`
  private int keycmp(byte xss[][], long xi, byte yss[][], long yi) {
    // Must be passed a left key and a right key to avoid call overhead of
    // extra arguments.  Only need left to left for equality only and that's
    // optimized in leftKeyEqual below.

    byte xbatch[] = xss[(int)(xi / _leftKO._batchSize)];
    byte ybatch[] = yss[(int)(yi / _riteKO._batchSize)];
    int xoff = (int)(xi % _leftKO._batchSize) * _leftSB._keySize;
    int yoff = (int)(yi % _riteKO._batchSize) * _riteSB._keySize;
    long xval=0, yval=0;

    // We avoid the NewChunk compression because we want finer grain
    // compression than 1,2,4 or 8 bytes types.  In particular, a range just
    // greater than 4bn can use 5 bytes rather than 8 bytes; a 38% RAM saving
    // over the wire in that possibly common case.  Note this is tight and
    // almost branch free.
    int i=0;
    while( i<_numJoinCols && xval==yval ) { // TODO: pass i in to start at a later key column, when known
      int xlen = _leftSB._fieldSizes[i];
      int ylen = _riteSB._fieldSizes[i];
      xval = xbatch[xoff] & 0xFFL; while (xlen>1) { xval <<= 8; xval |= xbatch[++xoff] & 0xFFL; xlen--; } xoff++;
      yval = ybatch[yoff] & 0xFFL; while (ylen>1) { yval <<= 8; yval |= ybatch[++yoff] & 0xFFL; ylen--; } yoff++;

      xval = xval==0 ? Long.MIN_VALUE : updateVal(xval,_leftSB._base[i]);
      yval = yval==0 ? Long.MIN_VALUE : updateVal(yval,_riteSB._base[i]);

      i++;
    }

    // The magnitude of the difference is used for limiting staleness in a
    // rolling join, capped at Integer.MAX|(MIN+1).  Roll's type is chosen to
    // be int so staleness can't be requested over int's limit.
    // Same return value as strcmp in C. <0 => xi<yi.
    long diff = xval-yval;  // could overflow even in long; e.g. joining to a prevailing NA, or very large gaps O(2^62)

    if (BigInteger.valueOf(xval).subtract(BigInteger.valueOf(yval)).bitLength() > 64)
      Log.warn("Overflow in BinaryMerge.java");  // detects overflow

    if (xval>yval) {        // careful not diff>0 here due to overflow
      return( (diff<0 | diff>Integer.MAX_VALUE  ) ? Integer.MAX_VALUE   : (int)diff);
    } else {
      return( (diff>0 | diff<Integer.MIN_VALUE+1) ? Integer.MIN_VALUE+1 : (int)diff);
    }
  }

  private long updateVal(Long oldVal, BigInteger baseD) {
    // we know oldVal is not zero
    BigInteger xInc = baseD.add(BigInteger.valueOf(oldVal).subtract(BigInteger.ONE));
    if (xInc.bitLength() > 64) {
      Log.warn("Overflow in BinaryMerge.java");
      return oldVal;  // should have died sooner or later
    } else
      return xInc.longValue();
  }

  // binary search to the left MSB in the 1st column only
  private long bsearchLeft(long x, boolean returnLow, long upp)  {
    long low = -1;
    while (low < upp - 1) {
      long mid = low + (upp - low) / 2;
      byte keyBatch[] = _leftKO._key[(int)(mid / _leftKO._batchSize)];
      int off = (int)(mid % _leftKO._batchSize) * _leftSB._keySize;
      int len = _leftSB._fieldSizes[0];
      long val = keyBatch[off] & 0xFFL; 
      while( len>1 ) { 
        val <<= 8; val |= keyBatch[++off] & 0xFFL; len--; 
      }

      val = val==0 ? Long.MIN_VALUE : updateVal(val,_leftSB._base[0]);
      if (x<val || (x==val && returnLow)) {
        upp = mid;
      } else {
        low = mid;
      }
    }
    return returnLow ? low : upp;
  }

  // Must be passed two leftKeys only.
  // Optimized special case for the two calling points; see usages in bmerge_r below.
  private boolean leftKeyEqual(byte x[][], long xi, long yi) {
    byte xbatch[] = x[(int)(xi / _leftKO._batchSize)];
    byte ybatch[] = x[(int)(yi / _leftKO._batchSize)];
    int xoff = (int)(xi % _leftKO._batchSize) * _leftSB._keySize;
    int yoff = (int)(yi % _leftKO._batchSize) * _leftSB._keySize;
    int i=0;
    while (i<_leftSB._keySize && xbatch[xoff++] == ybatch[yoff++]) i++;
    return(i==_leftSB._keySize);
  }

  private void bmerge_r(long lLowIn, long lUppIn, long rLowIn, long rUppIn) {
    // TODO: parallel each of the 256 bins
    long lLow = lLowIn, lUpp = lUppIn, rLow = rLowIn, rUpp = rUppIn;
    long mid, tmpLow, tmpUpp;
    // i.e. (lLow+lUpp)/2 but being robust to one day in the future someone
    // somewhere overflowing long; e.g. 32 exabytes of 1-column ints
    long lr = lLow + (lUpp - lLow) / 2;   
    while (rLow < rUpp - 1) {
      mid = rLow + (rUpp - rLow) / 2;
      int cmp = keycmp(_leftKO._key, lr, _riteKO._key, mid);  // -1, 0 or 1, like strcmp
      if (cmp < 0) {
        rUpp = mid;
      } else if (cmp > 0) {
        rLow = mid;
      } else { // rKey == lKey including NA == NA
        // branch mid to find start and end of this group in this column
        // TODO?: not if mult=first|last and col<ncol-1
        tmpLow = mid;
        tmpUpp = mid;
        while (tmpLow < rUpp - 1) {
          mid = tmpLow + (rUpp - tmpLow) / 2;
          if (keycmp(_leftKO._key, lr, _riteKO._key, mid) == 0) tmpLow = mid;
          else rUpp = mid;
        }
        while (rLow < tmpUpp - 1) {
          mid = rLow + (tmpUpp - rLow) / 2;
          if (keycmp(_leftKO._key, lr, _riteKO._key, mid) == 0) tmpUpp = mid;
          else rLow = mid;
        }
        break;
      }
    }
    // rLow and rUpp now surround the group in the right table.

    // The left table key may (unusually, and not recommended, but sometimes needed) be duplicated.
    // Linear search outwards from left row.  
    // Most commonly, the first test shows this left key is unique.
    // This saves (i) re-finding the matching rows in the right for all the
    // dup'd left and (ii) recursive bounds logic gets awkward if other left
    // rows can find the same right rows
    // Related to 'allow.cartesian' in data.table.
    // TODO: if index stores attribute that it is unique then we don't need
    // this step. However, each of these while()s would run at most once in
    // that case, which may not be worth optimizing.
    tmpLow = lr + 1;
    // TODO: these while's could be rolled up inside leftKeyEqual saving call overhead
    while (tmpLow<lUpp && leftKeyEqual(_leftKO._key, tmpLow, lr)) tmpLow++;  
    lUpp = tmpLow;
    tmpUpp = lr - 1;
    while (tmpUpp>lLow && leftKeyEqual(_leftKO._key, tmpUpp, lr)) tmpUpp--;
    lLow = tmpUpp;
    // lLow and lUpp now surround the group in the left table.  If left key is unique then lLow==lr-1 and lUpp==lr+1.
    assert lUpp - lLow >= 2;

    // if value found, rLow and rUpp surround it, unlike standard binary search where rLow falls on it
    long len = rUpp - rLow - 1;
    // TODO - we don't need loop here :)  Why does perNodeNumRightRowsToFetch increase so much?
    if (len > 0 || _allLeft) {
      long t0 = System.nanoTime();
      if (len > 1) _oneToManyMatch = true;
      _numRowsInResult += Math.max(1,len) * (lUpp-lLow-1);   // 1 for NA row when _allLeft
      for (long j = lLow + 1; j < lUpp; j++) {   // usually iterates once only for j=lr, but more than once if there are dup keys in left table
        // may be a range of left dup'd join-col values, but we need to fetch
        // each one since the left non-join columns are likely not dup'd and
        // may be the reason for the cartesian join
        long t00 = System.nanoTime();
        // TODO could loop through batches rather than / and % wastefully
        long globalRowNumber = _leftKO.at8order(j);
        _timings[17] += (System.nanoTime() - t00)/1e9;
        t00 = System.nanoTime();
        int chkIdx = _leftSB._vec.elem2ChunkIdx(globalRowNumber); //binary search in espc
        _timings[15] += (System.nanoTime() - t00)/1e9;
        // the key is the same within this left dup range, but still need to fetch left non-join columns
        _leftKO._perNodeNumRowsToFetch[_leftSB._chunkNode[chkIdx]]++;  
        if (len==0) continue;  // _allLeft must be true if len==0

        // TODO: initial MSB splits should split down to small enough chunk
        // size - but would that require more passes and if so, how long?  Code
        // simplification benefits would be welcome!
        long outLoc = j - (_leftFrom + 1);   // outOffset is 0 here in the standard scaling up high cardinality test
        // outBatchSize can be different, and larger since known to be 8 bytes
        // per item, both retFirst and retLen.  (Allowing 8 byte here seems
        // wasteful, actually.)
        final int jb2 = (int)(outLoc/_retBatchSize);  
        final int jo2 = (int)(outLoc%_retBatchSize);  // TODO - take outside the loop.  However when we go deep-msb, this'll go away.

        // rLow surrounds row, so +1.  Then another +1 for 1-based
        // row-number. 0 (default) means nomatch and saves extra set to -1 for
        // no match.  Could be significant in large edge cases by not needing
        // to write at all to _ret1st if it has no matches.
        _ret1st[jb2][jo2] = rLow + 2;  
        _retLen[jb2][jo2] = len;
      }

      // if we have dup'd left row, we only need to fetch the right rows once
      // for the first dup.  Those should then be recycled locally later.
      for (long i=0; i<len; i++) {
        long loc = rLow+1+i;
        long t00 = System.nanoTime();
        // TODO could loop through batches rather than / and % wastefully
        long globalRowNumber = _riteKO.at8order(loc);
        _timings[18] += (System.nanoTime() - t00)/1e9;
        t00 = System.nanoTime();
        int chkIdx = _riteSB._vec.elem2ChunkIdx(globalRowNumber); //binary search in espc
        _timings[16] += (System.nanoTime() - t00)/1e9;
        // just count the number per node. So we can allocate arrays precisely
        // up front, and also to return early to use in case of memory errors
        // or other distribution problems
        _riteKO._perNodeNumRowsToFetch[_riteSB._chunkNode[chkIdx]]++;  
      }
      _timings[14] += (System.nanoTime() - t0)/1e9;
    }
    // TODO: check assumption that retFirst and retLength are initialized to 0, for case of no match
    // Now branch (and TODO in parallel) to merge below and merge above

    // '|| _allLeft' is needed here in H2O (but not data.table) for the
    // _leftKO._perNodeNumRowsToFetch above to populate and pass the assert near
    // the end of the compute2() above.
    if (lLow > lLowIn && (rLow > rLowIn || _allLeft)) // '|| _allLeft' is needed here in H2O (but not data.table)
      bmerge_r(lLowIn, lLow+1, rLowIn, rLow+1);
    if (lUpp < lUppIn && (rUpp < rUppIn || _allLeft))
      bmerge_r(lUpp-1, lUppIn, rUpp-1, rUppIn);

    // We don't feel tempted to reduce the global _ansN here and make a global
    // frame, since we want to process each MSB l/r combo individually without
    // allocating them all.  Since recursive, no more code should be here (it
    // would run too much)
  }


  private void createChunksInDKV() {
    // Collect all matches
    // Create the final frame (part) for this MSB combination
    // Cannot use a List<Long> as that's restricted to 2Bn items and also isn't an Iced datatype
    long t0 = System.nanoTime(), t1;

    final int cloudSize = H2O.CLOUD.size();
    final long perNodeRightRows[][][] = new long[cloudSize][][];
    final long perNodeLeftRows [][][] = new long[cloudSize][][];
    // Allocate memory to split this MSB combn's left and right matching rows
    // into contiguous batches sent to the nodes they reside on
    for( int i = 0; i < cloudSize; i++ ) {
      perNodeRightRows[i] = _riteKO.fillPerNodeRows(i);
      perNodeLeftRows [i] = _leftKO.fillPerNodeRows(i);
    }
    _timings[2] += ((t1=System.nanoTime()) - t0) / 1e9; t0=t1;

    // Loop over _ret1st and _retLen and populate the batched requests for
    // each node helper.  _ret1st and _retLen are the same shape
    final long perNodeRightLoc[] = new long[cloudSize];
    final long perNodeLeftLoc [] = new long[cloudSize];
    chunksPopulatePerNode(perNodeLeftLoc,perNodeLeftRows,perNodeRightLoc,perNodeRightRows);
    _timings[3] += ((t1=System.nanoTime()) - t0) / 1e9; t0=t1;

    // Create the chunks for the final frame from this MSB pair.
    
    // 16 bytes for each UUID (biggest type). Enum will be long (8). TODO: How is non-Enum 'string' handled by H2O?
    final int batchSizeUUID = 256*1024*1024 / 16;  // number of rows per chunk to fit in 256GB DKV limit.
    final int nbatch = (int) ((_numRowsInResult-1)/batchSizeUUID +1);  // TODO: wrap in class to avoid this boiler plate
    assert nbatch >= 1;
    final int lastSize = (int) (_numRowsInResult - (nbatch-1)*batchSizeUUID);
    assert lastSize > 0;
    final int numLeftCols = _leftSB._frame.numCols();
    final int numColsInResult = _leftSB._frame.numCols() + _riteSB._frame.numCols() - _numJoinCols;
    final double[][][] frameLikeChunks = new double[numColsInResult][nbatch][]; //TODO: compression via int types
    BufferedString[][][] frameLikeChunks4Strings = new BufferedString[numColsInResult][nbatch][]; // cannot allocate before hand
    _chunkSizes = new int[nbatch];

    for (int col = 0; col < numColsInResult; col++) {
      if (this._stringCols[col]) {
        for (int b = 0; b < nbatch; b++) {
          frameLikeChunks4Strings[col][b] = new BufferedString[_chunkSizes[b] = (b == nbatch - 1 ? lastSize : batchSizeUUID)];
        }
      } else {
        for (int b = 0; b < nbatch; b++) {
          frameLikeChunks[col][b] = MemoryManager.malloc8d(_chunkSizes[b] = (b == nbatch - 1 ? lastSize : batchSizeUUID));
          Arrays.fill(frameLikeChunks[col][b], Double.NaN);
          // NA by default to save filling with NA for nomatches when allLeft
        }
      }
    }

    _timings[4] += ((t1=System.nanoTime()) - t0) / 1e9; t0=t1;

    // Get Raw Remote Rows
    final GetRawRemoteRows grrrsLeft[][] = new GetRawRemoteRows[cloudSize][];
    final GetRawRemoteRows grrrsRite[][] = new GetRawRemoteRows[cloudSize][];
    chunksGetRawRemoteRows(perNodeLeftRows,perNodeRightRows,grrrsLeft,grrrsRite);
    _timings[6] += ((t1=System.nanoTime()) - t0) / 1e9; t0=t1;  // all this time is expected to be in [5]

    // Now loop through _ret1st and _retLen and populate
    chunksPopulateRetFirst(numColsInResult, numLeftCols, perNodeLeftLoc, grrrsLeft, perNodeRightLoc, grrrsRite, frameLikeChunks, frameLikeChunks4Strings);
    _timings[10] += ((t1=System.nanoTime()) - t0) / 1e9; t0=t1;

    // compress all chunks and store them
    chunksCompressAndStore(nbatch, numColsInResult, frameLikeChunks, frameLikeChunks4Strings);
    _timings[11] += (System.nanoTime() - t0) / 1e9;
  }

  // Loop over _ret1st and _retLen and populate the batched requests for
  // each node helper.  _ret1st and _retLen are the same shape
  private void chunksPopulatePerNode( final long perNodeLeftLoc[], final long perNodeLeftRows[][][], final long perNodeRightLoc[], final long perNodeRightRows[][][] ) {
    final int batchSizeLong = 256*1024*1024 / 16;  // 256GB DKV limit / sizeof(UUID)
    long prevf = -1, prevl = -1;
    // TODO: hop back to original order here for [] syntax.
    long leftLoc=_leftFrom;  // sweep through left table along the sorted row locations.  
    for (int jb=0; jb<_ret1st.length; ++jb) {              // jb = j batch
      for (int jo=0; jo<_ret1st[jb].length; ++jo) {        // jo = j offset
        leftLoc++;  // to save jb*_ret1st[0].length + jo;
        long f = _ret1st[jb][jo];  // TODO: take _ret1st[jb] outside inner loop
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
          // TODO could loop through batches rather than / and % wastefully
          long row = _leftKO.at8order(leftLoc);
          int chkIdx = _leftSB._vec.elem2ChunkIdx(row); //binary search in espc
          int ni = _leftSB._chunkNode[chkIdx];
          long pnl = perNodeLeftLoc[ni]++;   // pnl = per node location
          perNodeLeftRows[ni][(int)(pnl/batchSizeLong)][(int)(pnl%batchSizeLong)] = row;  // ask that node for global row number row
        }
        if (f==0) continue;
        assert l > 0;
        if (prevf == f && prevl == l) 
          continue;  // don't re-fetch the same matching rows (cartesian). We'll repeat them locally later.
        prevf = f; prevl = l;
        for (int r=0; r<l; r++) {
          long loc = f+r-1;  // -1 because these are 0-based where 0 means no-match and 1 refers to the first row
          // TODO: could take / and % outside loop in cases where it doesn't span a batch boundary
          long row = _riteKO.at8order(loc);
          // find the owning node for the row, using local operations here
          int chkIdx = _riteSB._vec.elem2ChunkIdx(row); //binary search in espc
          int ni = _riteSB._chunkNode[chkIdx];
          // TODO Split to an if() and batch and offset separately
          long pnl = perNodeRightLoc[ni]++;   // pnl = per node location.
          perNodeRightRows[ni][(int)(pnl/batchSizeLong)][(int)(pnl%batchSizeLong)] = row;  // ask that node for global row number row
        }
      }
    }
    // TODO assert that perNodeRite and Left are exactly equal to the number
    // expected and allocated.
    Arrays.fill(perNodeLeftLoc ,0); // clear for reuse below
    Arrays.fill(perNodeRightLoc,0);
  }

  // Get Raw Remote Rows
  private void chunksGetRawRemoteRows(final long perNodeLeftRows[][][], final long perNodeRightRows[][][], GetRawRemoteRows grrrsLeft[][], GetRawRemoteRows grrrsRite[][]) {
    RPC<GetRawRemoteRows> grrrsRiteRPC[][] = new RPC[H2O.CLOUD.size()][];
    RPC<GetRawRemoteRows> grrrsLeftRPC[][] = new RPC[H2O.CLOUD.size()][];

    // Launch remote tasks left and right
    for( H2ONode node : H2O.CLOUD._memary ) {
      final int ni = node.index();
      final int bUppRite = perNodeRightRows[ni] == null ? 0 : perNodeRightRows[ni].length;
      final int bUppLeft =  perNodeLeftRows[ni] == null ? 0 :  perNodeLeftRows[ni].length;
      grrrsRiteRPC[ni] = new RPC[bUppRite];
      grrrsLeftRPC[ni] = new RPC[bUppLeft];
      grrrsRite[ni] = new GetRawRemoteRows[bUppRite];
      grrrsLeft[ni] = new GetRawRemoteRows[bUppLeft];
      for (int b = 0; b < bUppRite; b++) {
        // TODO try again now with better surrounding method
        // Arrays.sort(perNodeRightRows[ni][b]);  Simple quick test of fetching in monotonic order. Doesn't seem to help so far. 
        grrrsRiteRPC[ni][b] = new RPC<>(node, new GetRawRemoteRows(_riteSB._frame, perNodeRightRows[ni][b])).call();
      }
      for (int b = 0; b < bUppLeft; b++) {
        // Arrays.sort(perNodeLeftRows[ni][b]);
        grrrsLeftRPC[ni][b] = new RPC<>(node, new GetRawRemoteRows(_leftSB._frame, perNodeLeftRows[ni][b])).call();
      }
    }
    for( H2ONode node : H2O.CLOUD._memary ) {
      // TODO: just send and wait for first batch on each node and then .get() next batch as needed.
      int ni = node.index();
      final int bUppRite = perNodeRightRows[ni] == null ? 0 : perNodeRightRows[ni].length;
      for (int b = 0; b < bUppRite; b++)
        _timings[5] += (grrrsRite[ni][b] = grrrsRiteRPC[ni][b].get()).timeTaken;
      final int bUppLeft = perNodeLeftRows[ni] == null ? 0 :  perNodeLeftRows[ni].length;
      for (int b = 0; b < bUppLeft; b++)
        _timings[5] += (grrrsLeft[ni][b] = grrrsLeftRPC[ni][b].get()).timeTaken;
    }
  }

  // Now loop through _ret1st and _retLen and populate
  private void chunksPopulateRetFirst(final int numColsInResult, final int numLeftCols, final long perNodeLeftLoc[], final GetRawRemoteRows grrrsLeft[][], final long perNodeRightLoc[], final GetRawRemoteRows grrrsRite[][], final double[][][] frameLikeChunks, BufferedString[][][] frameLikeChunks4String) {
    // 16 bytes for each UUID (biggest type). Enum will be long (8). 
    // TODO: How is non-Enum 'string' handled by H2O?
    final int batchSizeUUID = 256*1024*1024 / 16;  // number of rows per chunk to fit in 256GB DKV limit.
    long resultLoc=0;   // sweep upwards through the final result, filling it in
    // TODO: hop back to original order here for [] syntax.
    long leftLoc=_leftFrom; // sweep through left table along the sorted row locations.  
    long prevf = -1, prevl = -1;
    for (int jb=0; jb<_ret1st.length; ++jb) {              // jb = j batch
      for (int jo=0; jo<_ret1st[jb].length; ++jo) {        // jo = j offset
        leftLoc++;  // to save jb*_ret1st[0].length + jo;
        long f = _ret1st[jb][jo];  // TODO: take _ret1st[jb] outside inner loop
        long l = _retLen[jb][jo];
        if (f==0 && !_allLeft) continue;  // f==0 => left row matches to no right row
        // else insert the left row once and NA for the right columns i.e. left outer join

        // Fetch the left rows and recycle it if more than 1 row in the right table is matched to.
        // TODO could loop through batches rather than / and % wastefully
        long row = _leftKO.at8order(leftLoc);
        // TODO should leftOrder and retFirst/retLen have the same batch size to make this easier?
        // TODO Can we not just loop through _leftKO._order only? Why jb and jo too through
        int chkIdx = _leftSB._vec.elem2ChunkIdx(row); //binary search in espc
        int ni = _leftSB._chunkNode[chkIdx];
        long pnl = perNodeLeftLoc[ni]++;   // pnl = per node location.  TODO: batch increment this rather than
        int b = (int)(pnl / batchSizeUUID);
        int o = (int)(pnl % batchSizeUUID);
        double[][] chks = grrrsLeft[ni][b]._chk;
        BufferedString[][] chksString = grrrsLeft[ni][b]._chkString;

        final int l1 = Math.max((int)l,1);
        for (int rep = 0; rep < l1; rep++) {
          long a = resultLoc + rep;
          // TODO: loop into batches to save / and % for each repeat and still
          // cater for crossing multiple batch boundaries
          int whichChunk = (int) (a / batchSizeUUID);  
          int offset = (int) (a % batchSizeUUID);

          for (int col=0; col<chks.length; col++) { // copy over left frame to frameLikeChunks
            if (this._stringCols[col]) {
              if (chksString[col][o] != null)
                frameLikeChunks4String[col][whichChunk][offset] = chksString[col][o];
            } else
              frameLikeChunks[col][whichChunk][offset] = chks[col][o];  // colForBatch.atd(row);
          }
        }
        if (f==0) { resultLoc++; continue; } // no match so just one row (NA for right table) to advance over
        assert l > 0;
        if (prevf == f && prevl == l) {
          // just copy from previous batch in the result (populated by for()
          // below).  Contiguous easy in-cache copy (other than batches).
          for (int r=0; r<l; r++) {
            // TODO: loop into batches to save / and % for each repeat and
            // still cater for crossing multiple batch boundaries
            int toChunk = (int) (resultLoc / batchSizeUUID);  
            int toOffset = (int) (resultLoc % batchSizeUUID);
            int fromChunk = (int) ((resultLoc - l) / batchSizeUUID);
            int fromOffset = (int) ((resultLoc - l) % batchSizeUUID);
            for (int col=0; col<numColsInResult-numLeftCols; col++) {
              int colIndex = numLeftCols + col;
              if (this._stringCols[colIndex]) {
                frameLikeChunks4String[colIndex][toChunk][toOffset] = frameLikeChunks4String[colIndex][fromChunk][fromOffset];
              } else {
                frameLikeChunks[colIndex][toChunk][toOffset] = frameLikeChunks[colIndex][fromChunk][fromOffset];
              }
            }
            resultLoc++;
          }
          continue;
        }
        prevf = f;
        prevl = l;
        for (int r=0; r<l; r++) {
          // TODO: loop into batches to save / and % for each repeat and still
          // cater for crossing multiple batch boundaries
          int whichChunk = (int) (resultLoc / batchSizeUUID);  
          int offset = (int) (resultLoc % batchSizeUUID);
          long loc = f+r-1;  // -1 because these are 0-based where 0 means no-match and 1 refers to the first row
          // TODO: could take / and % outside loop in cases where it doesn't span a batch boundary
          row = _riteKO.at8order(loc);
          // find the owning node for the row, using local operations here
          chkIdx = _riteSB._vec.elem2ChunkIdx(row); //binary search in espc
          ni = _riteSB._chunkNode[chkIdx];
          pnl = perNodeRightLoc[ni]++;   // pnl = per node location.   // TODO Split to an if() and batch and offset separately
          chks = grrrsRite[ni][(int)(pnl / batchSizeUUID)]._chk;
          chksString = grrrsRite[ni][(int)(pnl / batchSizeUUID)]._chkString;
          o = (int)(pnl % batchSizeUUID);
          for (int col=0; col<numColsInResult-numLeftCols; col++) {
            // TODO: this only works for numeric columns (not for UUID, strings, etc.)
            int colIndex = numLeftCols + col;
            if (this._stringCols[colIndex]) {
              if (chksString[_numJoinCols + col][o]!=null)
                frameLikeChunks4String[colIndex][whichChunk][offset] = chksString[_numJoinCols + col][o];  // colForBatch.atd(row);
            } else
              frameLikeChunks[colIndex][whichChunk][offset] = chks[_numJoinCols + col][o];  // colForBatch.atd(row);
          }
          resultLoc++;
        }
      }
    }
  }

  // compress all chunks and store them
  private void chunksCompressAndStore(final int nbatch, final int numColsInResult, final double[][][] frameLikeChunks, BufferedString[][][] frameLikeChunks4String) {
    // compress all chunks and store them
    Futures fs = new Futures();
    for (int col = 0; col < numColsInResult; col++) {
      if (this._stringCols[col]) {
        for (int b = 0; b < nbatch; b++) {
          NewChunk nc = new NewChunk(null, 0);
          for (int index = 0; index < frameLikeChunks4String[col][b].length; index++)
            nc.addStr(frameLikeChunks4String[col][b][index]);
          Chunk ck = nc.compress();
          DKV.put(getKeyForMSBComboPerCol(_leftSB._msb, _riteSB._msb, col, b), ck, fs, true);
          frameLikeChunks4String[col][b] = null; //free mem as early as possible (it's now in the store)
        }
      } else {
        for (int b = 0; b < nbatch; b++) {
          Chunk ck = new NewChunk(frameLikeChunks[col][b]).compress();
          DKV.put(getKeyForMSBComboPerCol(_leftSB._msb, _riteSB._msb, col, b), ck, fs, true);
          frameLikeChunks[col][b] = null; //free mem as early as possible (it's now in the store)
        }
      }
    }
    fs.blockForPending();
  }


  static Key getKeyForMSBComboPerCol(/*Frame leftFrame, Frame rightFrame,*/ int leftMSB, int rightMSB, int col /*final table*/, int batch) {
    return Key.make("__binary_merge__Chunk_for_col" + col + "_batch" + batch
                    // + rightFrame._key.toString() + "_joined_with" + leftFrame._key.toString()
                    + "_leftSB._msb" + leftMSB + "_riteSB._msb" + rightMSB,
            (byte) 1, Key.HIDDEN_USER_KEY, false, SplitByMSBLocal.ownerOfMSB(rightMSB==-1 ? leftMSB : rightMSB)
    ); //TODO home locally
  }

  static class GetRawRemoteRows extends DTask<GetRawRemoteRows> {
    Frame _fr;
    long[/*rows*/] _rows; //which rows to fetch from remote node, non-null on the way to remote, null on the way back

    double[/*col*/][] _chk; //null on the way to remote node, non-null on the way back
    BufferedString[][] _chkString;
    double timeTaken;
    GetRawRemoteRows(Frame fr, long[] rows) { _rows = rows;  _fr = fr; }

    @Override
    public void compute2() {
      assert(_rows!=null);
      assert(_chk ==null);
      long t0 = System.nanoTime();
      // System.out.print("Allocating _chk with " + _fr.numCols() +" by " + _rows.length + "...");
      _chk  = MemoryManager.malloc8d(_fr.numCols(),_rows.length);  // TODO: should this be transposed in memory?
      _chkString = new BufferedString[_fr.numCols()][_rows.length];
      // System.out.println("done");
      int cidx[] = MemoryManager.malloc4(_rows.length);
      int offset[] = MemoryManager.malloc4(_rows.length);
      Vec anyVec = _fr.anyVec();  assert anyVec != null;
      for (int row=0; row<_rows.length; row++) {
        cidx[row] = anyVec.elem2ChunkIdx(_rows[row]);  // binary search of espc array.  TODO: sort input row numbers to avoid
        offset[row] = (int)(_rows[row] - anyVec.espc()[cidx[row]]);
      }
      Chunk c[] = new Chunk[anyVec.nChunks()];
      for (int col=0; col<_fr.numCols(); col++) {
        Vec v = _fr.vec(col);
        for (int i=0; i<c.length; i++) c[i] = v.chunkKey(i).home() ? v.chunkForChunkIdx(i) : null;  // grab a chunk here
        if (v.isString()) {
          for (int row = 0; row < _rows.length; row++) {  // copy string and numeric columns
              _chkString[col][row] = c[cidx[row]].atStr(new BufferedString(), offset[row]); // _chkString[col][row] store by reference here
          }
        } else {
          for (int row = 0; row < _rows.length; row++) {  // extract info from chunks to one place
            _chk[col][row] = c[cidx[row]].atd(offset[row]);
          }
        }
      }

      // tell remote node to fill up Chunk[/*batch*/][/*rows*/]
      // perNodeRows[node] has perNodeRows[node].length batches of row numbers to fetch
      _rows=null;
      _fr=null;
      assert(_chk !=null);

      timeTaken = (System.nanoTime() - t0) / 1e9;

      tryComplete();
    }
  }
}
