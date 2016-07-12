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
    _cb = _vblocks[blockId].getChunkBlock(chunkId);
    return _cb.getChunk(vecId - _vecOffsets[_blockId]);
  }

  public Chunk[] getChunks(int cidx) {
    Chunk [] chks = new Chunk[len()];
    int k = 0;
    for(int i = 0; i < _vblocks.length; ++i) {
      ChunkBlock cb = _vblocks[i].getChunkBlock(cidx);
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
