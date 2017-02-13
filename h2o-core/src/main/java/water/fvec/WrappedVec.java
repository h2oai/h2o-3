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
    super(key, rowLayout, new String[][]{domain}, new byte[]{domain == null?Vec.T_NUM:Vec.T_CAT});
    _masterVec = masterVec;
  }

  public VecAry masterVec() { return _masterVec; }
  // Map from chunk-index to ByteArraySupportedChunk.  These wrappers are making custom Chunks
  abstract public DBlock chunkIdx(int cidx);
}
