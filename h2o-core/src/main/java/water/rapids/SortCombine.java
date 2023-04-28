package water.rapids;

import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.Log;

import java.util.Arrays;

/**
 * Before this class is called, sorting has been completed on the sorted columns.  The job of this class is to
 * gather the sorted rows of one MSB into chunks which will later be stitched together to form the whole frame.
 */
class SortCombine extends DTask<SortCombine> {
  long _numRowsInResult = 0;  // returned to caller, so not transient
  int _chunkSizes[]; // TODO:  only _chunkSizes.length is needed by caller, so return that length only
  double _timings[];

  final FFSB _leftSB;
  private transient KeyOrder _leftKO;

  private transient long _leftFrom;
  private transient int _retBatchSize;
  private int[][] _numRowsPerCidx;
  private int _chunkNum;
  private boolean[] _stringCols;
  private boolean[] _intCols;
  final SingleThreadRadixOrder.OXHeader _leftSortedOXHeader;
  final long _mergeId;
  
  // around the cluster.
  static class FFSB extends Iced<FFSB> {
    private final Frame _frame;
    private final Vec _vec;
    private final int _chunkNode[]; // Chunk homenode index
    final int _msb;

    FFSB(Frame frame, int msb) {
      assert -1 <= msb && msb <= 255; // left ranges from 0 to 255, right from -1 to 255
      _frame = frame;
      _msb = msb;
      // Create fast lookups to go from chunk index to node index of that chunk
      Vec vec = _vec = frame.anyVec();
      _chunkNode = vec == null ? null : MemoryManager.malloc4(vec.nChunks());
      if (vec == null) return; // Zero-columns for Sort
      for (int i = 0; i < _chunkNode.length; i++)
        _chunkNode[i] = vec.chunkKey(i).home_node().index();
    }
  }
  
  SortCombine(FFSB leftSB, SingleThreadRadixOrder.OXHeader leftSortedOXHeader, long mergeId) {
    _leftSB = leftSB;
    _mergeId = mergeId;
    int columnsInResult = _leftSB._frame.numCols();
    _stringCols = MemoryManager.mallocZ(columnsInResult);
    _intCols = MemoryManager.mallocZ(columnsInResult);
    if (_leftSB._frame!=null) {
      for (int col=0; col < _leftSB._frame.numCols(); col++) {
        if (_leftSB._frame.vec(col).isInt())
          _intCols[col] = true;
        if (_leftSB._frame.vec(col).isString())
          _stringCols[col] = true;
      }
    }
    _chunkNum = _leftSB._frame.anyVec().nChunks();
    _leftSortedOXHeader = leftSortedOXHeader;

  }

  @Override
  public void compute2() {
    _timings = MemoryManager.malloc8d(20);
    long t0 = System.nanoTime();
    _leftKO = new KeyOrder(_leftSortedOXHeader, _mergeId);
    _leftKO.initKeyOrder(_leftSB._msb,/*left=*/true);
    _retBatchSize = (int) _leftKO._batchSize;
    final long leftN = _leftSortedOXHeader._numRows; // store number of rows in left frame for the MSB
    assert leftN >= 1;
    _timings[0] += (System.nanoTime() - t0) / 1e9;
    
    _leftFrom = -1;
    long leftTo = leftN; // number of rows in left frame

    long retSize = leftTo - _leftFrom - 1;
    assert retSize >= 0;
    if (retSize == 0) {
      tryComplete();
      return;
    }
    
    _numRowsInResult = retSize;
    
    setPerNodeNumsToFetch();  // find out the number of rows to fetch from H2O nodes, number of rows to fetch per chunk
    
    if (_numRowsInResult > 0) createChunksInDKV(_mergeId);
    tryComplete();
  }

  /**
   * This method will find the number of rows per chunk to fetch per batch for a MSB
   */
  private void setPerNodeNumsToFetch() {
    Vec anyVec = _leftSB._frame.anyVec();
    int nbatch = _leftKO._order.length;
    _numRowsPerCidx = new int[nbatch][anyVec.nChunks()];
    for (int batchNum =0; batchNum < nbatch; batchNum++) {
      int batchSize = _leftKO._order[batchNum].length;
      for (int index=0; index < batchSize; index++) {
        long globalRowNumber = _leftKO._order[batchNum][index];
        int chkIdx = _leftSB._vec.elem2ChunkIdx(globalRowNumber);
        _leftKO._perNodeNumRowsToFetch[_leftSB._chunkNode[chkIdx]]++;
        _numRowsPerCidx[batchNum][chkIdx]++;  // number of rows to fetch per Cidx for a certain MSB
      }
    }
  }

  // Holder for Key & Order info
  private static class KeyOrder {
    public final long _batchSize;
    private final transient byte _key[/*n2GB*/][/*i mod 2GB * _keySize*/];
    private final transient long _order[/*n2GB*/][/*i mod 2GB * _keySize*/];
    private final transient long _perNodeNumRowsToFetch[];
    final long _mergeId;

    KeyOrder(SingleThreadRadixOrder.OXHeader sortedOXHeader, long mergeId) {
      _batchSize = sortedOXHeader._batchSize;
      final int nBatch = sortedOXHeader._nBatch;
      _key = new byte[nBatch][];
      _order = new long[nBatch][];
      _perNodeNumRowsToFetch = new long[H2O.CLOUD.size()];
      _mergeId = mergeId;
    }

    void initKeyOrder(int msb, boolean isLeft) {
      for (int b = 0; b < _key.length; b++) {
        Value v = DKV.get(SplitByMSBLocal.getSortedOXbatchKey(isLeft, msb, b, _mergeId));
        SplitByMSBLocal.OXbatch ox = v.get(); //mem version (obtained from remote) of the Values gets turned into POJO version
        v.freeMem(); //only keep the POJO version of the Value
        _key[b] = ox._x;
        _order[b] = ox._o;
      }
    }
  }

  private void chunksPopulatePerChunk(final long[][][] perNodeLeftRowsCidx, final long[][][] perNodeLeftIndices) {
    int numBatch = _leftKO._order.length; // note that _order is ordered as nbatch/batchIndex

    int[][] chkIndices = new int[numBatch][]; // store index for each batch per chunk
    for (int nbatch=0; nbatch < numBatch; nbatch++) {
      int sortedRowIndex = -1;
      chkIndices[nbatch] = new int[_chunkNum];
      int batchSize = _leftKO._order[nbatch].length;
      for (int batchNum=0; batchNum < batchSize; batchNum++) {
        sortedRowIndex++;
        long row = _leftKO._order[nbatch][batchNum];
        int chkIdx =  _leftSB._vec.elem2ChunkIdx(row); //binary search in espc
        perNodeLeftRowsCidx[nbatch][chkIdx][chkIndices[nbatch][chkIdx]] = row;
        perNodeLeftIndices[nbatch][chkIdx][chkIndices[nbatch][chkIdx]] = sortedRowIndex;
        chkIndices[nbatch][chkIdx]++;
      }
    }
  }
  
  private void createChunksInDKV(long mergeId) {
    long t0 = System.nanoTime(), t1;
    // Create the chunks for the final frame from this MSB.
    final int batchSizeUUID = _retBatchSize;
    final int nbatch = (int) ((_numRowsInResult - 1) / batchSizeUUID + 1);

    final int cloudSize = H2O.CLOUD.size();
    final long[][][] perMSBLeftRowsCidx = new long[nbatch][_chunkNum][]; // store the global row number per cidx
    final long[][][] perMSBLeftIndices = new long[nbatch][_chunkNum][];  // store the sorted index of that row 

    // allocate memory to arrays
    for (int batchN=0; batchN < nbatch; batchN++) {
    for (int chkidx=0; chkidx < _chunkNum; chkidx++) {
        perMSBLeftRowsCidx[batchN][chkidx] = new long[_numRowsPerCidx[batchN][chkidx]];
        perMSBLeftIndices[batchN][chkidx] = new long[_numRowsPerCidx[batchN][chkidx]];
      }
    }
    _timings[2] += ((t1 = System.nanoTime()) - t0) / 1e9;
    t0 = t1;
    
    chunksPopulatePerChunk(perMSBLeftRowsCidx, perMSBLeftIndices); // populate perMSBLeftRowsCidx and perMSBLeftIndices
    _timings[3] += ((t1 = System.nanoTime()) - t0) / 1e9;
    t0 = t1;
    
    assert nbatch >= 1;
    final int lastSize = (int) (_numRowsInResult - (nbatch - 1) * batchSizeUUID); // if there is only 1 batch, this will be the size
    assert lastSize > 0;
    final int numColsInResult = _leftSB._frame.numCols();
    final double[][][] frameLikeChunks = new double[numColsInResult][nbatch][]; //TODO: compression via int types
    final long[][][] frameLikeChunksLongs = new long[numColsInResult][nbatch][]; //TODO: compression via int types
    BufferedString[][][] frameLikeChunks4Strings = new BufferedString[numColsInResult][nbatch][]; // cannot allocate before hand
    _chunkSizes = new int[nbatch];
    final GetRawRemoteRowsPerChunk grrrsLeftPerChunk[][] = new GetRawRemoteRowsPerChunk[cloudSize][];
    
    for (int b = 0; b < nbatch; b++) {  // divide rows of a MSB into batches to process to avoid overwhelming a machine
      allocateFrameLikeChunks(b, nbatch, lastSize, batchSizeUUID, frameLikeChunks, frameLikeChunks4Strings,
              frameLikeChunksLongs, numColsInResult); // allocate memory for frameLikeChunks...

      chunksPopulateRetFirstPerChunk(perMSBLeftRowsCidx, perMSBLeftIndices, b, grrrsLeftPerChunk, frameLikeChunks,
              frameLikeChunks4Strings, frameLikeChunksLongs); // fetch and populate rows of a MSB one batch at a time.

      _timings[10] += ((t1 = System.nanoTime()) - t0) / 1e9;
      t0 = t1;
      // compress all chunks and store them
      chunksCompressAndStore(b, numColsInResult, frameLikeChunks, frameLikeChunks4Strings, frameLikeChunksLongs, mergeId);
      if (nbatch > 1) {
        cleanUpMemory(grrrsLeftPerChunk, b);  // clean up memory used by grrrsLeftperChunk
      }
    }

    _timings[11] += (System.nanoTime() - t0) / 1e9;
  }

  // collect all rows with the same MSB one batch at a time over all nodes in a sorted fashion
  private void chunksPopulateRetFirstPerChunk(final long[][][] perMSBLeftRowsCidx, final long[][][] perMSBLeftIndices,
                                              final int jb, final GetRawRemoteRowsPerChunk grrrsLeft[][], final double[][][] frameLikeChunks,
                                              BufferedString[][][] frameLikeChunks4String, final long[][][] frameLikeChunksLong) {
    RPC<GetRawRemoteRowsPerChunk> grrrsLeftRPC[][] = new RPC[H2O.CLOUD.size()][];
    int batchSize = _leftKO._order[jb].length;

    for (H2ONode node : H2O.CLOUD._memary) {
      final int ni = node.index();
      grrrsLeftRPC[ni] = new RPC[1];
      grrrsLeft[ni] = new GetRawRemoteRowsPerChunk[1];
      grrrsLeftRPC[ni][0] = new RPC<>(node, new GetRawRemoteRowsPerChunk(_leftSB._frame, batchSize,
              perMSBLeftRowsCidx[jb], perMSBLeftIndices[jb])).call();
    }

    for (H2ONode node : H2O.CLOUD._memary) {
      int ni = node.index();
      _timings[5] += (grrrsLeft[ni][0] = grrrsLeftRPC[ni][0].get()).timeTaken;
    }

    for (H2ONode node : H2O.CLOUD._memary) {
      final int ni = node.index();
      // transfer entries from _chk to frameLikeChunks
      long[][] chksLong = grrrsLeft[ni][0]._chkLong;  // indexed by col num per batchsize
      double[][] chks = grrrsLeft[ni][0]._chk;
      BufferedString[][] chksString = grrrsLeft[ni][0]._chkString;

      for (int cidx = 0; cidx < _chunkNum; cidx++) {
        if (_leftSB._chunkNode[cidx] == ni) { // copy over rows from this node
          int rowSize = perMSBLeftIndices[jb][cidx].length;
          for (int row = 0; row < rowSize; row++) {
            for (int col = 0; col < chks.length; col++) {
              int offset = (int) perMSBLeftIndices[jb][cidx][row];
              if (this._stringCols[col]) {
                frameLikeChunks4String[col][jb][offset] = chksString[col][offset];
              } else if (this._intCols[col]) {
                frameLikeChunksLong[col][jb][offset] = chksLong[col][offset];
              } else {
                frameLikeChunks[col][jb][offset] = chks[col][offset];
              }
            }
          }
        }
      }
    }
  }
  
  private void allocateFrameLikeChunks(final int b, final int nbatch, final int lastSize, final int batchSizeUUID,
                                       final double[][][] frameLikeChunks,
                                       final BufferedString[][][] frameLikeChunks4Strings,
                                       final long[][][] frameLikeChunksLongs, final int numColsInResult) {
    for (int col = 0; col < numColsInResult; col++) {  // allocate memory for frameLikeChunks for this batch
      if (this._stringCols[col]) {
        frameLikeChunks4Strings[col][b] = new BufferedString[_chunkSizes[b] = (b == nbatch - 1 ? lastSize : batchSizeUUID)];
      } else if (this._intCols[col]) {
        frameLikeChunksLongs[col][b] = MemoryManager.malloc8(_chunkSizes[b] = (b == nbatch - 1 ? lastSize : batchSizeUUID));
        Arrays.fill(frameLikeChunksLongs[col][b], Long.MIN_VALUE);
      } else {
        frameLikeChunks[col][b] = MemoryManager.malloc8d(_chunkSizes[b] = (b == nbatch - 1 ? lastSize : batchSizeUUID));
        Arrays.fill(frameLikeChunks[col][b], Double.NaN);
        // NA by default to save filling with NA for nomatches when allLeft
      }
    }
  }
  
  
  private void cleanUpMemory(GetRawRemoteRowsPerChunk[][] grrr, int batchIdx) {
    if (grrr != null) {
      int nodeNum = grrr.length;
      for (int nodeIdx = 0; nodeIdx < nodeNum; nodeIdx++) {
        int batchLimit = Math.min(batchIdx + 1, grrr[nodeIdx].length);
        if ((grrr[nodeIdx] != null) && (grrr[nodeIdx].length > 0)) {
          for (int bIdx = 0; bIdx < batchLimit; bIdx++) { // clean up memory
            int chkLen = grrr[nodeIdx][bIdx] == null ? 0 :
                    (grrr[nodeIdx][bIdx]._chk == null ? 0 : grrr[nodeIdx][bIdx]._chk.length);
            for (int cindex = 0; cindex < chkLen; cindex++) {
              grrr[nodeIdx][bIdx]._chk[cindex] = null;
              grrr[nodeIdx][bIdx]._chkString[cindex] = null;
              grrr[nodeIdx][bIdx]._chkLong[cindex] = null;
            }
            if (chkLen > 0) {
              grrr[nodeIdx][bIdx]._chk = null;
              grrr[nodeIdx][bIdx]._chkString = null;
              grrr[nodeIdx][bIdx]._chkLong = null;
            }
          }
        }
      }
    }
  }

  // compress all chunks and store them
  private void chunksCompressAndStore(final int b, final int numColsInResult, final double[][][] frameLikeChunks,
                                      BufferedString[][][] frameLikeChunks4String, final long[][][] frameLikeChunksLong, 
                                      long mergeId) {
    // compress all chunks and store them
    Futures fs = new Futures();
    for (int col = 0; col < numColsInResult; col++) {
      if (this._stringCols[col]) {
        NewChunk nc = new NewChunk(null, 0);
        for (int index = 0; index < frameLikeChunks4String[col][b].length; index++)
          nc.addStr(frameLikeChunks4String[col][b][index]);
        Chunk ck = nc.compress();
        DKV.put(BinaryMerge.getKeyForMSBComboPerCol(_leftSB._msb, -1, col, b, mergeId), ck, fs, true);
        frameLikeChunks4String[col][b] = null; //free mem as early as possible (it's now in the store)
      } else if (_intCols[col]) {
        NewChunk nc = new NewChunk(null, -1);
        for (long l : frameLikeChunksLong[col][b]) {
          if (l == Long.MIN_VALUE) nc.addNA();
          else nc.addNum(l, 0);
        }
        Chunk ck = nc.compress();
        DKV.put(BinaryMerge.getKeyForMSBComboPerCol(_leftSB._msb, -1, col, b, mergeId), ck, fs, true);
        frameLikeChunksLong[col][b] = null; //free mem as early as possible (it's now in the store)
      } else {
        Chunk ck = new NewChunk(frameLikeChunks[col][b]).compress();
        DKV.put(BinaryMerge.getKeyForMSBComboPerCol(_leftSB._msb, -1, col, b, mergeId), ck, fs, true);
        frameLikeChunks[col][b] = null; //free mem as early as possible (it's now in the store)
      }
    }
    fs.blockForPending();
  }

  static class GetRawRemoteRowsPerChunk extends DTask<GetRawRemoteRowsPerChunk> {
    Frame _fr;
    long[][] _perNodeLeftIndices;
    long[][] _perNodeLeftRowsCidx;
    double[/*col*/][] _chk; //null on the way to remote node, non-null on the way back
    BufferedString[][] _chkString;
    long[/*col*/][] _chkLong;
    int _batchSize;  // deal with which batch we are working with
    int _nChunks;

    double timeTaken;

    GetRawRemoteRowsPerChunk(Frame fr, int batchSize, long[][] leftRowsCidx, long[][] leftRowsIndices) {
      _fr = fr;
      _batchSize = batchSize; // size of current batch we are dealing with
      _perNodeLeftIndices = leftRowsIndices;
      _perNodeLeftRowsCidx = leftRowsCidx;
      _nChunks = _perNodeLeftIndices.length; // number of chunks in fr.
    }

    private static long[][] malloc8A(int m, int n) {
      long[][] res = new long[m][];
      for (int i = 0; i < m; ++i)
        res[i] = MemoryManager.malloc8(n);
      return res;
    }

    @Override
    public void compute2() {
      assert ((_perNodeLeftIndices != null) && (_perNodeLeftRowsCidx != null));
      assert (_chk == null);
      long t0 = System.nanoTime();
      _chk = MemoryManager.malloc8d(_fr.numCols(), _batchSize);
      _chkLong = malloc8A(_fr.numCols(), _batchSize);
      _chkString = new BufferedString[_fr.numCols()][_batchSize];
      for (int cidx = 0; cidx < _nChunks; cidx++) { // go through each chunk and copy from frame to sorted arrays
        for (int col = 0; col < _fr.numCols(); col++) {
          Vec v = _fr.vec(col);
          if (!v.chunkKey(cidx).home())
            break;  // goto next cidex

          Chunk c = v.chunkForChunkIdx(cidx);
          int chunkSize = _perNodeLeftRowsCidx[cidx].length;
          if (v.isString()) {
            for (int row = 0; row < chunkSize; row++) {  // copy string and numeric columns
              int offset = (int) (_perNodeLeftRowsCidx[cidx][row] - v.espc()[cidx]);  // row number
              _chkString[col][(int) _perNodeLeftIndices[cidx][row]] = c.atStr(new BufferedString(), offset); // _chkString[col][row] store by reference here
            }
          } else if (v.isInt()) {
            for (int row = 0; row < chunkSize; row++) {  // extract info from chunks to one place
              int offset = (int) (_perNodeLeftRowsCidx[cidx][row] - v.espc()[cidx]);  // row number
              _chkLong[col][(int) _perNodeLeftIndices[cidx][row]] = (c.isNA(offset)) ? Long.MIN_VALUE : c.at8(offset);
            }
          } else {
            for (int row = 0; row < chunkSize; row++) {  // extract info from chunks to one place
              int offset = (int) (_perNodeLeftRowsCidx[cidx][row] - v.espc()[cidx]);  // row number
              _chk[col][(int) _perNodeLeftIndices[cidx][row]] = c.atd(offset);
            }

          }
        }
      }
      _perNodeLeftIndices = null;
      _perNodeLeftRowsCidx = null;
      _fr = null;
      assert (_chk != null && _chkLong != null && _chkString != null);
      timeTaken = (System.nanoTime() - t0) / 1e9;
      tryComplete();
    }
  }
}
