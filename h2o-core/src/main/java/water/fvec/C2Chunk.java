package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in shorts.
 */
public class C2Chunk extends Chunk {
  static protected final long _NA = Short.MIN_VALUE;
  static protected final int _OFF=0;
  C2Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>1); }
  @Override protected final long at8_impl( int i ) {
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get2(_mem,(i<<1)+_OFF) == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Short.MIN_VALUE < l && l <= Short.MAX_VALUE) ) return false;
    UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)l);
    return true;
  }
  @Override boolean set_impl(int idx, double d) {
    if( Double.isNaN(d) ) return setNA_impl(idx);
    long l = (long)d;
    return l == d && set_impl(idx, l);
  }
  @Override boolean set_impl(int i, float f ) { return set_impl(i,(double)f); }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)_NA); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(0);
    nc.set_len(0);
    final int len = _len;
    for( int i=0; i<len; i++ ) {
      int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
      if( res == _NA ) nc.addNA();
      else             nc.addNum(res,0);
    }
    return nc;
  }
  @Override public C2Chunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>1);
    assert _mem.length == _len <<1;
    return this;
  }
  @Override
  public boolean hasFloat() {return false;}
}
