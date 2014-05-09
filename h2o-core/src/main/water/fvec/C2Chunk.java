package water.fvec;

import water.*;

/**
 * The empty-compression function, where data is in shorts.
 */
public class C2Chunk extends Chunk {
  static protected final long _NA = Short.MIN_VALUE;
  static final int OFF=0;
  C2Chunk( byte[] bs ) { _mem=bs; _start = -1; _len = _mem.length>>1; }
  @Override protected final long at8_impl( int i ) {
    int res = UDP.get2(_mem,(i<<1)+OFF);
    if( res == _NA ) throw new IllegalArgumentException("at8 but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    int res = UDP.get2(_mem,(i<<1)+OFF);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UDP.get2(_mem,(i<<1)+OFF) == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Short.MIN_VALUE < l && l <= Short.MAX_VALUE) ) return false;
    UDP.set2(_mem,(idx<<1)+OFF,(short)l);
    return true;
  }
  @Override boolean set_impl(int idx, double d) {
    if( Double.isNaN(d) ) return setNA_impl(idx);
    long l = (long)d;
    return l == d && set_impl(idx, l);
  }
  @Override boolean set_impl(int i, float f ) { return set_impl(i,(double)f); }
  @Override boolean setNA_impl(int idx) { UDP.set2(_mem,(idx<<1)+OFF,(short)_NA); return true; }
  @Override NewChunk inflate_impl(NewChunk nc) {
    nc._xs = MemoryManager.malloc4(_len);
    nc._ls = MemoryManager.malloc8(_len);
    for( int i=0; i<_len; i++ ) {
      int res = UDP.get2(_mem,(i<<1)+OFF);
      if( res == C2Chunk._NA ) nc._xs[i] = Integer.MIN_VALUE;
      else                     nc._ls[i] = res;
    }
    return nc;
  }
  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem,_mem.length); }
  @Override public C2Chunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _len = _mem.length>>1;
    assert _mem.length == _len<<1;
    return this;
  }
}
