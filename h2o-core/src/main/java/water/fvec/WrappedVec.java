package water.fvec;

import water.*;

/**  A simple wrapper over another Vec.  Transforms either data values or rows.
 */
abstract class WrappedVec extends Vec {
  /** A key for underlying vector which contains values which are transformed by this vector. */
  final Key<Vec> _masterVecKey;
  /** Cached instances of underlying vector. */
  transient Vec _masterVec;
  public WrappedVec(Key key, int rowLayout, Key masterVecKey ) { this(key,rowLayout,null,masterVecKey);  }
  public WrappedVec(Key key, int rowLayout, String[] domain, Key masterVecKey) {
    super(key, rowLayout, domain);
    _masterVecKey = masterVecKey;
  }

  public Vec masterVec() { return _masterVec!=null ? _masterVec : (_masterVec = _masterVecKey.get()); }
  // Map from chunk-index to Chunk.  These wrappers are making custom Chunks
  abstract public Chunk chunkForChunkIdx(int cidx);
}
