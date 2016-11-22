package water.fvec;

import water.udf.TypedChunk;

/**
 * Representative of TypedChunk in the world of typeless chunks. 
 * We don't store any data there. We just pretend to be a "regular" chunk,
 * to satisfy the obsolete API.
 */
public class RawChunk extends CXIChunk {

  public RawChunk(TypedChunk<?> base) {
    super(base.length(), 0, new byte[6]);
    _cidx = base.cidx();
    _vec = base.vec();
    this._start = base.start();
  }
}
