package water.fvec;

import water.*;

/**  A simple wrapper over another Vec.  Transforms either data values or rows.
 */
abstract class WrappedVec extends Vec {
  /** A key for underlying vector which contains values which are transformed by this vector. */
  final VecAry _masterVec;
  /** Cached instances of underlying vector. */
  public WrappedVec(Key key, int rowLayout, VecAry masterVec ) { this(key,rowLayout,null,masterVec);  }
  public WrappedVec(Key key, int rowLayout, String[] domain, VecAry masterVec) {
    super(key, rowLayout, domain);
    assert masterVec.len() == 1;
    _masterVec = masterVec;
  }
  public VecAry masterVec() { return _masterVec; }
  // Map from chunk-index to Chunk.  These wrappers are making custom Chunks
  abstract public SingleChunk chunkForChunkIdx(int cidx);
}
