package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'long's.
 */
public class C8Chunk extends Chunk {
  protected static final long _NA = Long.MIN_VALUE;
  C8Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>3); }
  @Override protected final long at8_impl( int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get8(_mem, i << 3)==_NA; }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set8(_mem,(idx<<3),_NA); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    for( int i=0; i< _len; i++ )
      if(isNA(i))nc.addNA();
      else nc.addNum(at8(i),0);
    nc.set_sparseLen(nc.set_len(_len));
    return nc;
  }
  @Override public C8Chunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>3);
    assert _mem.length == _len <<3;
    return this;
  }
  @Override
  public boolean hasFloat() {return false;}
}
