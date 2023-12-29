package water.fvec;

import water.DKV;
import water.Futures;
import water.Key;


/* Bare minimal implementation intended to be used only by `SplitApplyCombine`*/
public class OneChunkVec extends WrappedVec {

  private int _chunkIdx;
  private transient Chunk _chunk;
  
  public OneChunkVec(Key<Vec> key, int rowLayout, Key<Vec> masterVecKey, int chunkIdx) {
    super(key, rowLayout, masterVecKey.get().domain(), masterVecKey);
    _chunkIdx = chunkIdx;
    _type = masterVec()._type;
  }


  public static OneChunkVec make(Vec v, int cidx, Futures fs) {
    OneChunkVec ocv = new OneChunkVec(Vec.newKey(Key.make(v._key+"_oneChunkVec_"+cidx)), -1, v._key, cidx);
    DKV.put(ocv, fs);
    return ocv;
  }


  @Override
  public int elem2ChunkIdx(long i) {
    return 0;
  }

  @Override
  public Vec masterVec() {
    return super.masterVec();
  }

  @Override
  public Chunk chunkForChunkIdx(int cidx) {
    assert cidx==0;
    if (null == _chunk) {
      _chunk = masterVec().chunkForChunkIdx(_chunkIdx).clone();
      _chunk.setStart(0);
      _chunk._vec = this;
    }
    return _chunk;
  }

  @Override
  public long length() {
    return chunkLen(0);
  }

  @Override
  public int nChunks() {
    return 1;
  }

  @Override
  public int chunkLen(int cidx) {
    assert cidx==0;
    return chunkForChunkIdx(0).len();
  }

  @Override
  long chunk2StartElem(int cidx) {
    return 0;
  }

  @Override
  public String toString() {
    return"OneChunkVec["+_chunkIdx+"] ("+masterVec().toString()+")";
  }
  
}
