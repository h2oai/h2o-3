package water.fvec;

import water.*;

/**
 * The empty-compression function, where data is in 'int's.
 */
public class C4Chunk extends Chunk {
  static protected final long _NA = Integer.MIN_VALUE;
  C4Chunk( byte[] bs ) { _mem=bs; _start = -1; _len = _mem.length>>2; }
  @Override protected final long at8_impl( int i ) {
    long res = UDP.get4(_mem,i<<2);
    if( res == _NA ) throw new IllegalArgumentException("at8 but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = UDP.get4(_mem,i<<2);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UDP.get4(_mem,i<<2) == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Integer.MIN_VALUE < l && l <= Integer.MAX_VALUE) ) return false;
    UDP.set4(_mem,idx<<2,(int)l);
    return true;
  }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UDP.set4(_mem,(idx<<2),(int)_NA); return true; }
  @Override NewChunk inflate_impl(NewChunk nc) {
    throw H2O.unimpl();
  }
  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem,_mem.length); }
  @Override public C4Chunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _len = _mem.length>>2;
    assert _mem.length == _len<<2;
    return this;
  }
}
