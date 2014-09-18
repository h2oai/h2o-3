package water.fvec;

import water.*;

/** 
 * A simple wrapper over another Vec.  Transforms either data values or rows.
 */
public abstract class WrappedVec extends Vec {
  /** A key for underlying vector which contains values which are transformed by this vector. */
  final Key  _masterVecKey;
  /** Cached instances of underlying vector. */
  transient Vec _masterVec;
  public WrappedVec(Key masterVecKey, Key key, long[] espc) {
    super(key, espc);
    _masterVecKey = masterVecKey;
  }

  @Override protected Vec masterVec() {
    return _masterVec!=null ? _masterVec : (_masterVec = _masterVecKey.get());
  }
  // Map from chunk-index to Chunk.  These wrappers are making custom Chunks
  abstract public Chunk chunkForChunkIdx(int cidx);
}
