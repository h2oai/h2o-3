package water;

import water.fvec.*;
import water.util.ArrayUtils;
import java.util.Arrays;

/**
 * Created by tomas on 7/7/16.
 */
public class VecAry extends Iced {
  VecBlock[] _vblocks;
  int [][] _vecIds;
  int [] _vecOffsets;

  public VecAry(VecBlock vblock, int []ids){
    this(new VecBlock[]{vblock}, new int[][]{ids});
  }
  public VecAry(VecBlock [] vblocks, int [][] ids){
    _vblocks = vblocks;
    _vecIds = ids;
    _vecOffsets = new int[vblocks.length+1];
    int len = 0;
    for(int i = 0; i < _vblocks.length; ++i) {
      len += ids[i].length;
      _vecOffsets[i+1] = len;
    }
  }

  public boolean isInt(int col) {
    throw H2O.unimpl(); // TODO
  }

  public VecAry makeZeros(int i) {
    throw H2O.unimpl();
  }

  public long[] espc() {
    throw H2O.unimpl();
  }

  public String[][] domains() {
    throw H2O.unimpl(); // TODO
  }

  public byte[] types() {
    throw H2O.unimpl(); // TODO
  }

  public int elem2ChunkId(long l) {
    int res = Arrays.binarySearch(espc(),l);
    return res < 0?(-res-2):res;
  }

  public void remove() {
    Futures fs = new Futures();
    remove(fs);
    fs.blockForPending();
  }

  public VecAry deepCopy() {
    throw H2O.unimpl();
  }

  /** A more efficient way to read randomly to a Vec - still single-threaded,
   *  but much faster than Vec.at(i).  Limited to single-threaded
   *  single-machine reads.
   *
   * Usage:
   * Vec.Reader vr = vec.new Reader();
   * x = vr.at(0);
   * y = vr.at(1);
   * z = vr.at(2);
   */
  public class VecReader {

    private ChunkBlock _cache;
    long _start;
    protected Chunk chk(long rowId) {
      throw H2O.unimpl(); // TODO
    }
    public final long    at8( long rowId) { return chk(rowId).at8((int)(rowId-_start)); }
    public final double   at( long rowId) { return chk(rowId).at8((int)(rowId-_start)); }
    public final boolean isNA(long rowId) { return chk(rowId).isNA((int)(rowId-_start)); }
    public final long length() { return numRows(); }
  }

  public final class VecWriter extends VecReader {
    public final void set  ( long rowId, long val)   { chk(rowId).set((int)(rowId-_start), val); }
    public final void set  ( long rowId, double val) { chk(rowId).set((int)(rowId-_start), val); }
    public final void setNA( long rowId) { chk(rowId).setNA((int)(rowId-_start)); }
    public Futures close(Futures fs){
      throw H2O.unimpl(); // TODO
    }

    public void close() {
      Futures fs = new Futures();
      close(fs);
      fs.blockForPending();
    }
  }

  public VecReader vecReader(int vecId) {
    throw H2O.unimpl(); // TODO
  }

  public VecWriter vecWriter(int vecId) {
    throw H2O.unimpl(); // TODO
  }


  public Futures remove(Futures fs) {
    for(VecBlock vb:_vblocks)
      vb.remove(fs);
    return fs;
  }

  public VecAry getVecs(int [] ids) {
    throw H2O.unimpl(); // TODO
  }
  public void addVecs(VecAry vs) {
    _vblocks = ArrayUtils.join(_vblocks,vs._vblocks);
    _vecIds = ArrayUtils.add(_vecIds,vs._vecIds);
    int n = _vecOffsets.length -1 ;
    int len = _vecOffsets[n];
    _vecOffsets = ArrayUtils.join(_vecOffsets,vs._vecOffsets);
    for(int j = n; j < _vecOffsets.length; ++j)
      _vecOffsets[j] += len;
  }
  public int len(){return _vecOffsets[_vecOffsets.length-1];}

  public Vec.VectorGroup group() {
    return _vblocks[0].group();
  }

  public int nChunks() {
    throw H2O.unimpl(); // TODO
  }

  VecBlock anyBlock(){return _vblocks[0];}

  public void close() {for(VecBlock vb:_vblocks) vb.close();}

  public Futures postWrite(Futures fs) {
    throw H2O.unimpl(); // TODO
  }

  public boolean isCompatible(VecAry vecs) {
    throw H2O.unimpl(); // TODO
  }

  public long numRows() {
    throw H2O.unimpl(); // TODO
  }

  public void swap(int lo, int hi) {
    throw H2O.unimpl(); // TODO
  }

  public VecAry subRange(int startIdx, int endIdx) {
    throw H2O.unimpl(); // TODO
  }

  public VecAry removeVecs(int... id) {
    throw H2O.unimpl(); // TODO
  }

  public VecAry removeRange(int startIdx, int endIdx) {
    throw H2O.unimpl(); // TODO
  }

  public static class SparseChunks {
    public int [] ids;
    public Chunk [] chks;
  }

  public RollupStats getRollups(int vecId) {
    if(vecId < 0 || vecId > len()) throw new ArrayIndexOutOfBoundsException(vecId);
    int blockId = Arrays.binarySearch(_vecOffsets,vecId);
    if(blockId < 0) blockId = -blockId - 1;
    return _vblocks[blockId].getRollups(vecId-_vecOffsets[blockId]);
  }

  private transient int _chunkId = -1;
  private transient int _blockId = 0;
  private transient ChunkBlock _cb = null;

  public Chunk getChunk(int chunkId, int vecId){
    if(_cb != null && _chunkId == chunkId && _vecOffsets[_blockId] <= vecId && vecId < _vecOffsets[_blockId+1])
      return _cb.getChunk(vecId - _vecOffsets[_blockId]);
    if(vecId < 0 || vecId > len()) throw new ArrayIndexOutOfBoundsException(vecId);
    int blockId = Arrays.binarySearch(_vecOffsets,vecId);
    if(blockId < 0) blockId = -blockId - 1;
    _chunkId = chunkId;
    _blockId = blockId;
    _cb = _vblocks[blockId].getChunkBlock(chunkId, true);
    return _cb.getChunk(vecId - _vecOffsets[_blockId]);
  }

  public Chunk[] getChunks(int cidx) {return getChunks(cidx,true);}
  public Chunk[] getChunks(int cidx, boolean cache) {
      Chunk [] chks = new Chunk[len()];
      int k = 0;
      for(int i = 0; i < _vblocks.length; ++i) {
        ChunkBlock cb = _vblocks[i].getChunkBlock(cidx,cache);
        for (int j = 0; j < _vecIds[i].length; ++j)
          chks[k++] = cb.getChunk(_vecIds[i][j]);
      }
      return chks;
  }

  public boolean isSparse(int cidx){return false;}

  public SparseChunks getSparseChunks(int cidx){
    return null; // TODO
  }

}
