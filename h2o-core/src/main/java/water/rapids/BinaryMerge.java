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

public class BinaryMerge extends DTask<BinaryMerge> {
  long _retFirst[/*n2GB*/][];  // The row number of the first right table's index key that matches
  long _retLen[/*n2GB*/][];    // How many rows does it match to?
  byte _leftKey[/*n2GB*/][/*i mod 2GB * _keySize*/];
  byte _rightKey[][];
  long _leftOrder[/*n2GB*/][/*i mod 2GB * _keySize*/];
  long _rightOrder[][];
  boolean _allLen1 = true;
  int _leftFieldSizes[], _rightFieldSizes[];  // the widths of each column in the key
  int _leftKeyNCol, _rightKeyNCol;  // the number of columns in the key i.e. length of _leftFieldSizes and _rightFieldSizes
  int _leftKeySize, _rightKeySize;   // the total width in bytes of the key, sum of field sizes
  int _numJoinCols;
  int _leftNodeIdx;
  long _leftN, _rightN;
  long _leftBatchSize, _rightBatchSize;
  Frame _leftFrame, _rightFrame;
  long _ansN=0;   // the number of rows in the resulting dataset
  final boolean _outerJoin = true; // TODO: add the option to do inner join (currently this flag just used to count a 1 for the NA for nomatch=NA)
  long _perNodeNumRowsToFetch[] = new long[H2O.CLOUD.size()];
  int _leftMSB, _rightMSB;

  BinaryMerge(Frame leftFrame, Frame rightFrame, int leftMSB, int rightMSB, int leftFieldSizes[], int rightFieldSizes[]) {   // In X[Y], 'left'=i and 'right'=x
    _leftFrame = leftFrame;
    _rightFrame = rightFrame;
    _leftMSB = leftMSB;
    _rightMSB = rightMSB;
    SingleThreadRadixOrder.OXHeader leftSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(_leftFrame._key, _leftMSB));
    SingleThreadRadixOrder.OXHeader rightSortedOXHeader = DKV.getGet(getSortedOXHeaderKey(_rightFrame._key, _rightMSB));
    if (leftSortedOXHeader == null || rightSortedOXHeader == null) return;
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
  }

  @Override
  protected void compute2() {
    if (_leftN != 0 && _rightN != 0) {
      // do the work
      bmerge_r(-1, _leftN, -1, _rightN);

      //put stuff into DKV
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
    if (len > 0) {
      _ansN += len;
      if (len > 1) _allLen1 = false;
      for (long j = lLow + 1; j < lUpp; j++) {   // usually iterates once only for j=lr, but more than once if there are dup keys in left table
        int jb = (int)(j/_leftBatchSize);
        int jo = (int)(j%_leftBatchSize);
        _retFirst[jb][jo] = rLow + 1;
        _retLen[jb][jo] = len;
        StringBuilder sb = new StringBuilder();
        sb.append("Left row " + _leftOrder[jb][jo] + " matches to " + _retLen[jb][jo] + " right rows: ");
        long a = _retFirst[jb][jo];
        for (int i=0; i<_retLen[jb][jo]; ++i) {
          long loc = a+i;
          sb.append(_rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)] + " ");
          long globalRowNumber = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];
          int chkIdx = _rightFrame.anyVec().elem2ChunkIdx(globalRowNumber); //binary search in espc
          H2ONode node = _rightFrame.anyVec().chunkKey(chkIdx).home_node(); //bit mask ops on the vec key
          _perNodeNumRowsToFetch[node.index()]++;  // just count the number per node. So we can allocate arrays precisely up front, and also to return early to use in case of memory errors or other distribution problems
        }
        Log.info(sb);
      }
    } else {
      if (_outerJoin) _ansN++;   // 1 NA row
    }
    // TO DO: check assumption that retFirst and retLength are initialized to 0, for case of no match
    // Now branch (and TO DO in parallel) to merge below and merge above
    if (lLow > lLowIn && rLow > rLowIn)
      bmerge_r(lLowIn, lLow + 1, rLowIn, rLow+1);
    if (lUpp < lUppIn && rUpp < rUppIn)
      bmerge_r(lUpp-1, lUppIn, rUpp-1, rUppIn);

    // Collect all matches
    // Create the final frame (part) for this MSB
    // Cannot use a List<Long> as that's restricted to 2Bn items and also isn't an Iced datatype
    long perNodeRows[][][] = new long[H2O.CLOUD.size()][][];
    long perNodeRowFrom[][][] = new long[H2O.CLOUD.size()][][];
    int batchSize = (int)_leftBatchSize;  // TODO: what's the right batch size here. And why is _leftBatchSize type long?
    for (int i=0; i<H2O.CLOUD.size(); i++) {
      if (_perNodeNumRowsToFetch[i] == 0) continue;
      int nbatch = (int) (_perNodeNumRowsToFetch[i]-1)/batchSize +1;  // TODO: wrap in class to avoid this boiler plate
      int lastSize = (int)(_perNodeNumRowsToFetch[i] - (nbatch-1)*batchSize);
      assert nbatch >= 1;
      assert lastSize > 0;
      perNodeRows[i] = new long[nbatch][];
      perNodeRowFrom[i] = new long[nbatch][];
      int b;
      for (b = 0; b < nbatch-1; b++) {
        perNodeRows[i][b] = new long[batchSize];  // TO DO?: use MemoryManager.malloc()
        perNodeRowFrom[i][b] = new long[batchSize];
      }
      perNodeRows[i][b] = new long[lastSize];
      perNodeRowFrom[i][b] = new long[lastSize];
    }

    // Loop over _retFirst and _retLen and assign the matching rows to the right node request so as to make one batched call
    for (int jb=0; jb<_leftBatchSize; ++jb) {
      for (int jo=0; jo<_retLen[jb].length; ++jo) {
        long a = _retFirst[jb][jo];
        if (a==0) continue;
        for (int r=0; r<_retLen[jb][jo]; ++r) {
          long loc = a+r;
          long row = _rightOrder[(int)(loc / _rightBatchSize)][(int)(loc % _rightBatchSize)];   // TODO: could take / and % outside loop in cases where it doesn't span a batch boundary
          // find the owning node for the row, using local operations here
          int chkIdx = _rightFrame.anyVec().elem2ChunkIdx(row); //binary search in espc
          H2ONode node = _rightFrame.anyVec().chunkKey(chkIdx).home_node(); //bit mask ops on the vec key
          perNodeRows[node.index()][(int)(row/batchSize)][(int)(row%batchSize)] = row;  // ask that node for row row
          perNodeRowFrom[node.index()][(int)(row/batchSize)][(int) (row % batchSize)] = jb*_leftBatchSize + jo;   // TODO: could store the batch and offset separately?  If it will be used to assign into a Vec, then that's have different shape/espc so the location is better.
        }
      }
    }

    // Fill the output columns coming from the right frame
    Vec[] vecs = new Vec[_rightFrame.numCols()];
    for (int i=0; i<vecs.length; ++i) {
      vecs[i] = Vec.makeCon(0, _ansN);
    }
    String[] names = _rightFrame.names().clone();
    Frame fr = new Frame(Key.make(_rightFrame._key.toString() + "_joined_with_" + _leftFrame._key.toString() + " on_some_columns_right_half_forLeftMSB_"+_leftMSB + "_RightMSB_" + _rightMSB), names, vecs);

    for (H2ONode node : H2O.CLOUD._memary) {
      for (int b=0; b<perNodeRows[node.index()].length; ++b) {
        GetRawRemoteRows grrr = new GetRawRemoteRows(_rightFrame, perNodeRows[node.index()][b]);
        H2O.submitTask(grrr);
        grrr.join();
        assert(grrr._rows==null);
        Chunk[] chk = grrr._chk;
        for (int col =0; col <chk.length; ++col) {
          Chunk colForBatch = chk[col];
          //HACK START
          Vec.Writer w = fr.vec(col).open();
          for (int row=0; row<colForBatch.len(); ++row) {
            double val = colForBatch.atd(row);
            long actualRowInMSBCombo = perNodeRowFrom[node.index()][b][row];
            w.set(actualRowInMSBCombo, val); //writes into fr.vec(col)
          }
          w.close();
          //HACK END
        }
      }
    }

  }

  class GetRawRemoteRows extends DTask<GetRawRemoteRows> {
    Chunk[/*col*/] _chk; //null on the way to remote node, non-null on the way back
    long[/*rows*/] _rows; //which rows to fetch from remote node, non-null on the way to remote, null on the way back
    Frame _rightFrame;
    GetRawRemoteRows(Frame rightFrame, long[] rows) {
      _rows = rows;
      _rightFrame = rightFrame;
      _chk  = new Chunk[_rightFrame.numCols()];
    }

    @Override
    protected void compute2() {
      assert(_rows!=null);
      assert(_chk ==null);

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
    }
  }
}
