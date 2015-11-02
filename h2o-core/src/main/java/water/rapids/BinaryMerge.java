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
import water.util.Log;

import java.util.Arrays;

public class BinaryMerge extends DTask<BinaryMerge> {
  long _retFirst[/*n2GB*/][];  // The row number of the first right table's index key that matches
  long _retLen[/*n2GB*/][];    // How many rows does it match to?
  byte _leftKey[/*n2GB*/][/*i mod 2GB * _keySize*/];
  byte _rightKey[][];
  long _leftOrder[/*n2GB*/][/*i mod 2GB * _keySize*/];
  long _rightOrder[][];
  boolean _oneToManyMatch = false;   // does any left row match to more than 1 right row?  If not, can allocate and loop more efficiently, and mark the resulting key'd frame with a 'unique' index.
                                     // TODO: implement
  int _leftFieldSizes[], _rightFieldSizes[];  // the widths of each column in the key
  int _leftKeyNCol, _rightKeyNCol;  // the number of columns in the key i.e. length of _leftFieldSizes and _rightFieldSizes
  int _leftKeySize, _rightKeySize;   // the total width in bytes of the key, sum of field sizes
  int _numJoinCols;
  long _leftN, _rightN;
  long _leftBatchSize, _rightBatchSize;
  Frame _leftFrame, _rightFrame;
  long _numRowsInResult=0;
  long _perNodeNumRightRowsToFetch[] = new long[H2O.CLOUD.size()];
  long _perNodeNumLeftRowsToFetch[] = new long[H2O.CLOUD.size()];
  int _leftMSB, _rightMSB;
  int _chunkSizes[];
  boolean _allLeft, _allRight;

  BinaryMerge(Frame leftFrame, Frame rightFrame, int leftMSB, int rightMSB, int leftFieldSizes[], int rightFieldSizes[], boolean allLeft) {   // In X[Y], 'left'=i and 'right'=x
    _leftFrame = leftFrame;
    _rightFrame = rightFrame;
    _leftMSB = leftMSB;
    _rightMSB = rightMSB;
    _allLeft = allLeft;
    _allRight = false;
    SingleThreadRadixOrder.OXHeader leftSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(_leftFrame._key, _leftMSB));
    SingleThreadRadixOrder.OXHeader rightSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(_rightFrame._key, _rightMSB));
    if (leftSortedOXHeader == null) {
      if (_allRight) throw H2O.unimpl();  // TO DO pass through _allRight and implement
      return;
    }
    if (rightSortedOXHeader == null) {
      if (_allLeft == false) return;
      rightSortedOXHeader = new SingleThreadRadixOrder.OXHeader(0, 0, 0);  // enables general case code to run below without needing new special case code
    }
    // both left and right MSB have some data to match
    _leftBatchSize = leftSortedOXHeader._batchSize;
    _rightBatchSize = rightSortedOXHeader._batchSize;

    // get left batches
    _leftKey = new byte[leftSortedOXHeader._nBatch][];
    _leftOrder = new long[leftSortedOXHeader._nBatch][];
    _retFirst = new long[leftSortedOXHeader._nBatch][];
    _retLen = new long[leftSortedOXHeader._nBatch][];
    for (int b=0; b<leftSortedOXHeader._nBatch; ++b) {
      MoveByFirstByte.OXbatch oxLeft = DKV.getGet(MoveByFirstByte.getSortedOXbatchKey(_leftFrame._key, _leftMSB, b));
      _leftKey[b] = oxLeft._x;
      _leftOrder[b] = oxLeft._o;
      _retFirst[b] = new long[oxLeft._o.length];
      _retLen[b] = new long[oxLeft._o.length];
    }
    _leftN = leftSortedOXHeader._numRows;

    // get right batches
    _rightKey = new byte[rightSortedOXHeader._nBatch][];
    _rightOrder = new long[rightSortedOXHeader._nBatch][];
    for (int b=0; b<rightSortedOXHeader._nBatch; ++b) {
      MoveByFirstByte.OXbatch oxRight = DKV.getGet(MoveByFirstByte.getSortedOXbatchKey(_rightFrame._key, _rightMSB, b));
      _rightKey[b] = oxRight._x;
      _rightOrder[b] = oxRight._o;
    }
    _rightN = rightSortedOXHeader._numRows;

    //_leftNodeIdx = leftNodeIdx;
    _leftFieldSizes = leftFieldSizes;
    _rightFieldSizes = rightFieldSizes;
    _leftKeyNCol = _leftFieldSizes.length;
    _rightKeyNCol = _rightFieldSizes.length;
    _leftKeySize = ArrayUtils.sum(leftFieldSizes);
    _rightKeySize = ArrayUtils.sum(rightFieldSizes);
    _numJoinCols = Math.min(_leftKeyNCol, _rightKeyNCol);
    _allLeft = allLeft;
  }

  @Override
  protected void compute2() {
    if ((_leftN != 0 || _allRight) && (_rightN != 0 || _allLeft)) {
      bmerge_r(-1, _leftN, -1, _rightN);
      if (_numRowsInResult > 0) createChunksInDKV();
    }

    //null out members before returning to calling node
    tryComplete();
  }


  private int keycmp(byte x[][], long xi, byte y[][], long yi) {   // TO DO - faster way closer to CPU like batches of long compare, maybe.
    byte xByte=0, yByte=0;
    xi *= _leftKeySize;
    yi *= _rightKeySize;   // x[] and y[] are len keys.
    // TO DO: rationalize x and y being chunked into 2GB pieces.  Take x[0][] and y[0][] outside loop / function
    // TO DO: switch to use keycmp_sameShape() for common case of all(leftFieldSizes == rightFieldSizes), although, skipping to current column will
    //        help save repeating redundant work and saving the outer for() loop and one if() may not be worth it.
    int i=0, xlen=0, ylen=0, diff=0;
    while (i<_numJoinCols && xlen==0) {    // TO DO: pass i in to start at a later key column, when known
      xlen = _leftFieldSizes[i];
      ylen = _rightFieldSizes[i];
      if (xlen!=ylen) {
        while (xlen>ylen && x[0][(int)xi]==0) { xi++; xlen--; }
        while (ylen>xlen && y[0][(int)yi]==0) { yi++; ylen--; }
        if (xlen!=ylen) return (xlen - ylen);
      }
      while (xlen>0 && (xByte=x[0][(int)xi])==(yByte=y[0][(int)yi])) { xi++; yi++; xlen--; }
      i++;
    }
    return (xByte & 0xFF) - (yByte & 0xFF);
    // Same return value as strcmp in C. <0 => xi<yi
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
    while (tmpLow<lUpp && keycmp(_leftKey, tmpLow, _leftKey, lr)==0) tmpLow++;
    lUpp = tmpLow;
    tmpUpp = lr - 1;
    while (tmpUpp>lLow && keycmp(_leftKey, tmpUpp, _leftKey, lr)==0) tmpUpp--;
    lLow = tmpUpp;
    // lLow and lUpp now surround the group in the left table.  If left key is unique then lLow==lr-1 and lUpp==lr+1.

    long len = rUpp - rLow - 1;  // if value found, rLow and rUpp surround it, unlike standard binary search where rLow falls on it
    if (len > 0 || _allLeft) {
      if (len > 1) _oneToManyMatch = true;
      _numRowsInResult += Math.max(1,len) * (lUpp-lLow-1);   // 1 for NA row when _allLeft
      for (long j = lLow + 1; j < lUpp; j++) {   // usually iterates once only for j=lr, but more than once if there are dup keys in left table
        {
          long globalRowNumber = _leftOrder[(int)(lr / _leftBatchSize)][(int)(lr % _leftBatchSize)];
          int chkIdx = _leftFrame.anyVec().elem2ChunkIdx(globalRowNumber); //binary search in espc
          H2ONode node = _leftFrame.anyVec().chunkKey(chkIdx).home_node(); //bit mask ops on the vec key
          _perNodeNumLeftRowsToFetch[node.index()]++;  // the key is the same within this left dup range, but still need to fetch left non-join columns
        }
        if (len==0) continue;  // _allLeft must be true if len==0
        int jb = (int)(j/_leftBatchSize);
        int jo = (int)(j%_leftBatchSize);
        _retFirst[jb][jo] = rLow + 2;  // rLow surrounds row, so +1.  Then another +1 for 1-based row-number. 0 (default) means nomatch and saves extra set to -1 for no match.  Could be significant in large edge cases by not needing to write at all to _retFirst if it has no matches.
        _retLen[jb][jo] = len;
        //StringBuilder sb = new StringBuilder();
        //sb.append("Left row " + _leftOrder[jb][jo] + " matches to " + _retLen[jb][jo] + " right rows: ");
        long a = _retFirst[jb][jo] -1;
        for (int i=0; i<_retLen[jb][jo]; i++) {
          long loc = a+i;
          //sb.append(_rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)] + " ");
          long globalRowNumber = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];
          int chkIdx = _rightFrame.anyVec().elem2ChunkIdx(globalRowNumber); //binary search in espc
          H2ONode node = _rightFrame.anyVec().chunkKey(chkIdx).home_node(); //bit mask ops on the vec key
          _perNodeNumRightRowsToFetch[node.index()]++;  // just count the number per node. So we can allocate arrays precisely up front, and also to return early to use in case of memory errors or other distribution problems
        }
        //Log.info(sb);
      }
    }
    // TO DO: check assumption that retFirst and retLength are initialized to 0, for case of no match
    // Now branch (and TO DO in parallel) to merge below and merge above
    if (lLow > lLowIn && rLow > rLowIn)
      bmerge_r(lLowIn, lLow + 1, rLowIn, rLow+1);
    if (lUpp < lUppIn && rUpp < rUppIn)
      bmerge_r(lUpp-1, lUppIn, rUpp-1, rUppIn);

    // We don't feel tempted to reduce the global _ansN here and make a global frame,
    // since we want to process each MSB l/r combo individually without allocating them all.
    // Since recursive, no more code should be here (it would run too much)
  }

  private void createChunksInDKV() {

    // Collect all matches
    // Create the final frame (part) for this MSB combination
    // Cannot use a List<Long> as that's restricted to 2Bn items and also isn't an Iced datatype
    long perNodeRightRows[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeRightRowsFrom[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeRightLoc[] = new long[H2O.CLOUD.size()];

    long perNodeLeftRows[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeLeftRowsFrom[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeLeftRowsRepeat[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeLeftLoc[] = new long[H2O.CLOUD.size()];

    // Allocate memory to split this MSB combn's left and right matching rows into contiguous batches sent to the nodes they reside on
    int batchSize = (int) _leftBatchSize;  // TODO: what's the right batch size here. And why is _leftBatchSize type long?
    for (int i = 0; i < H2O.CLOUD.size(); i++) {
      if (_perNodeNumRightRowsToFetch[i] > 0) {
        int nbatch = (int) ((_perNodeNumRightRowsToFetch[i] - 1) / batchSize + 1);  // TODO: wrap in class to avoid this boiler plate
        int lastSize = (int) (_perNodeNumRightRowsToFetch[i] - (nbatch - 1) * batchSize);
        assert nbatch >= 1;
        assert lastSize > 0;
        perNodeRightRows[i] = new long[nbatch][];
        perNodeRightRowsFrom[i] = new long[nbatch][];
        int b;
        for (b = 0; b < nbatch - 1; b++) {
          perNodeRightRows[i][b] = new long[batchSize];  // TO DO?: use MemoryManager.malloc()
          perNodeRightRowsFrom[i][b] = new long[batchSize];
        }
        perNodeRightRows[i][b] = new long[lastSize];
        perNodeRightRowsFrom[i][b] = new long[lastSize];
      }
      if (_perNodeNumLeftRowsToFetch[i] > 0) {
        int nbatch = (int) ((_perNodeNumLeftRowsToFetch[i] - 1) / batchSize + 1);  // TODO: wrap in class to avoid this boiler plate
        int lastSize = (int) (_perNodeNumLeftRowsToFetch[i] - (nbatch - 1) * batchSize);
        assert nbatch >= 1;
        assert lastSize > 0;
        perNodeLeftRows[i] = new long[nbatch][];
        perNodeLeftRowsFrom[i] = new long[nbatch][];
        perNodeLeftRowsRepeat[i] = new long[nbatch][];
        int b;
        for (b = 0; b < nbatch - 1; b++) {
          perNodeLeftRows[i][b] = new long[batchSize];  // TO DO?: use MemoryManager.malloc()
          perNodeLeftRowsFrom[i][b] = new long[batchSize];
          perNodeLeftRowsRepeat[i][b] = new long[batchSize];
        }
        perNodeLeftRows[i][b] = new long[lastSize];
        perNodeLeftRowsFrom[i][b] = new long[lastSize];
        perNodeLeftRowsRepeat[i][b] = new long[lastSize];
      }
    }

    // Loop over _retFirst and _retLen and populate the batched requests for each node helper
    // _retFirst and _retLen are the same shape
    long resultLoc=0;  // sweep upwards through the final result, filling it in
    long leftLoc=-1;   // sweep through left table along the sorted row locations.  // TODO: hop back to original order here for [] syntax.
    for (int jb=0; jb<_retFirst.length; ++jb) {              // jb = j batch
      for (int jo=0; jo<_retFirst[jb].length; ++jo) {        // jo = j offset
        leftLoc++;  // to save jb*_retFirst[0].length + jo;
        long f = _retFirst[jb][jo];
        long l = _retLen[jb][jo];
        if (f==0) {
          // left row matches to no right row
          assert l == 0;  // doesn't have to be 0 (could be 1 already if allLeft==true) but currently it should be, so check it
          if (!_allLeft) continue;
          // now insert the left row once and NA for the right columns i.e. left outer join
        }
        { // new scope so 'row' can be declared in the for() loop below and registerized (otherwise 'already defined in this scope' in that scope)
          // Fetch the left rows and mark the contiguous from-ranges each left row should be recycled over
          long row = _leftOrder[(int)(leftLoc / _leftBatchSize)][(int)(leftLoc % _leftBatchSize)];
          int chkIdx = _leftFrame.anyVec().elem2ChunkIdx(row); //binary search in espc
          H2ONode node = _leftFrame.anyVec().chunkKey(chkIdx).home_node(); //bit mask ops on the vec key
          long pnl = perNodeLeftLoc[node.index()]++;   // pnl = per node location
          perNodeLeftRows[node.index()][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = row;  // ask that node for global row number row
          perNodeLeftRowsFrom[node.index()][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = resultLoc;  // TODO: could store the batch and offset separately?  If it will be used to assign into a Vec, then that's have different shape/espc so the location is better.
          perNodeLeftRowsRepeat[node.index()][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = Math.max(1,l);
        }
        if (f==0) { resultLoc++; continue; }
        assert l > 0;
        for (int r=0; r<l; r++) {
          long loc = f+r-1;  // -1 because these are 0-based where 0 means no-match and 1 refers to the first row
          long row = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];   // TODO: could take / and % outside loop in cases where it doesn't span a batch boundary
          // find the owning node for the row, using local operations here
          int chkIdx = _rightFrame.anyVec().elem2ChunkIdx(row); //binary search in espc
          H2ONode node = _rightFrame.anyVec().chunkKey(chkIdx).home_node(); //bit mask ops on the vec key
          long pnl = perNodeRightLoc[node.index()]++;   // pnl = per node location
          perNodeRightRows[node.index()][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = row;  // ask that node for global row number row
          perNodeRightRowsFrom[node.index()][(int)(pnl/batchSize)][(int)(pnl%batchSize)] = resultLoc++;  // TODO: could store the batch and offset separately?  If it will be used to assign into a Vec, then that's have different shape/espc so the location is better.
        }
      }
    }

    // Create the chunks for the final frame from this MSB pair.
    batchSize = 1<<22; // number of rows per chunk.  32MB for doubles, 64MB for UUIDs to fit into 256MB DKV Value limit
    int nbatch = (int) (_numRowsInResult-1)/batchSize +1;  // TODO: wrap in class to avoid this boiler plate
    int lastSize = (int)(_numRowsInResult - (nbatch-1)*batchSize);
    assert nbatch >= 1;
    assert lastSize > 0;
    _chunkSizes = new int[nbatch];
    int _numLeftCols = _leftFrame.numCols();
    int _numColsInResult = _leftFrame.numCols() + _rightFrame.numCols() - _numJoinCols;
    double[][][] frameLikeChunks = new double[_numColsInResult][nbatch][]; //TODO: compression via int types
    for (int col=0; col<_numColsInResult; col++) {
      int b;
      for (b = 0; b < nbatch - 1; b++) {
        frameLikeChunks[col][b] = new double[batchSize];
        Arrays.fill(frameLikeChunks[col][b], Double.NaN);   // NA by default to save filling with NA for nomatches when allLeft
        _chunkSizes[b] = batchSize;
      }
      frameLikeChunks[col][b] = new double[lastSize];
      Arrays.fill(frameLikeChunks[col][b], Double.NaN);
      _chunkSizes[b] = lastSize;
    }

    for (H2ONode node : H2O.CLOUD._memary) {
      int bUpp = perNodeRightRows[node.index()] == null ? 0 : perNodeRightRows[node.index()].length;
      for (int b = 0; b < bUpp; b++) {
        GetRawRemoteRows grrr = new GetRawRemoteRows(_rightFrame, perNodeRightRows[node.index()][b]);
        H2O.submitTask(grrr);
        grrr.join();
        assert (grrr._rows == null);
        Chunk[] chk = grrr._chk;
        for (int col = _numJoinCols; col < chk.length; col++) {   // TODO: currently join columns must be the first _numJoinCols. Relax.
          Chunk colForBatch = chk[col];
          for (int row = 0; row < colForBatch.len(); row++) {
            double val = colForBatch.atd(row); //TODO: this only works for numeric columns (not for date, UUID, strings, etc.)
            long actualRowInMSBCombo = perNodeRightRowsFrom[node.index()][b][row];
            int whichChunk = (int) (actualRowInMSBCombo / batchSize);
            int offset = (int) (actualRowInMSBCombo % batchSize);
            frameLikeChunks[_numLeftCols - 1 + col][whichChunk][offset] = val;
          }
        }
      }
      bUpp = perNodeLeftRows[node.index()] == null ? 0 : perNodeLeftRows[node.index()].length;
      for (int b = 0; b < bUpp; ++b) {
        GetRawRemoteRows grrr = new GetRawRemoteRows(_leftFrame, perNodeLeftRows[node.index()][b]);
        H2O.submitTask(grrr);
        grrr.join();
        assert (grrr._rows == null);
        Chunk[] chk = grrr._chk;
        for (int col = 0; col < chk.length; ++col) {
          Chunk colForBatch = chk[col];
          for (int row = 0; row < colForBatch.len(); row++) {
            double val = colForBatch.atd(row); //TODO: this only works for numeric columns (not for date, UUID, strings, etc.)
            long actualRowInMSBCombo = perNodeLeftRowsFrom[node.index()][b][row];
            for (int rep = 0; rep < perNodeLeftRowsRepeat[node.index()][b][row]; rep++) {
              long a = actualRowInMSBCombo + rep;
              int whichChunk = (int) (a / batchSize);  // TO DO: loop into batches to save / and % for each repeat and still cater for crossing multiple batch boundaries
              int offset = (int) (a % batchSize);
              frameLikeChunks[col][whichChunk][offset] = val;
            }
          }
        }
      }
    }

    // compress all chunks and store them
    Futures fs = new Futures();
    for (int col=0; col<_numColsInResult; col++) {
      for (int b = 0; b < nbatch; b++) {
        Chunk ck = new NewChunk(frameLikeChunks[col][b]).compress();
        DKV.put(getKeyForMSBComboPerCol(_leftFrame, _rightFrame, _leftMSB, _rightMSB, col, b), ck, fs, true);
        frameLikeChunks[col][b]=null; //free mem as early as possible (it's now in the store)
      }
    }
    fs.blockForPending();
  }

  static Key getKeyForMSBComboPerCol(Frame leftFrame, Frame rightFrame, int leftMSB, int rightMSB, int col /*final table*/, int batch) {
    return Key.make("__binary_merge__Chunk_for_col_" + col + "_batch_" + batch
            + rightFrame._key.toString() + "_joined_with" + leftFrame._key.toString()
            + "_forLeftMSB_"+leftMSB + "_RightMSB_" + rightMSB,
            (byte)1, Key.HIDDEN_USER_KEY, false, MoveByFirstByte.ownerOfMSB(rightMSB)
            ); //TODO home locally
  }

  class GetRawRemoteRows extends DTask<GetRawRemoteRows> {
    Chunk[/*col*/] _chk; //null on the way to remote node, non-null on the way back
    long[/*rows*/] _rows; //which rows to fetch from remote node, non-null on the way to remote, null on the way back
    Frame _rightFrame;
    GetRawRemoteRows(Frame rightFrame, long[] rows) {
      _rows = rows;
      _rightFrame = rightFrame;
    }

    @Override
    protected void compute2() {
      assert(_rows!=null);
      assert(_chk ==null);
      _chk  = new Chunk[_rightFrame.numCols()];

      double[][] rawVals = new double[_rightFrame.numCols()][_rows.length];
      for (int col=0; col<_rightFrame.numCols(); ++col) {
        Vec v = _rightFrame.vec(col);
        for (int row=0; row<_rows.length; ++row) {
          rawVals[col][row] = v.at(_rows[row]); //local reads, random access //TODO: use chunk accessors by using indirection array
        }
        _chk[col] = new NewChunk(rawVals[col]);
      }

      // tell remote node to fill up Chunk[/*batch*/][/*rows*/]
//      perNodeRows[node] has perNodeRows[node].length batches of row numbers to fetch
      _rows=null;
      assert(_chk !=null);
      tryComplete();
    }
  }
}
