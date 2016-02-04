package water.fvec;

import water.AutoBuffer;
import water.MemoryManager;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'double's.
 */
public class C8DChunk extends Chunk {
  C8DChunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>3); }

  @Override protected final long   at8_impl( int i ) {
    double res = UnsafeUtils.get8d(_mem, i << 3);
    if( Double.isNaN(res) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }
  @Override protected final double   atd_impl( int i ) { return              UnsafeUtils.get8d(_mem,i<<3) ; }
  @Override protected final boolean isNA_impl( int i ) { return Double.isNaN(UnsafeUtils.get8d(_mem,i<<3)); }
  @Override boolean set_impl(int idx, long l) { return false; }

  /**
   * Fast explicit set for double.
   * @param i
   * @param d
   */
  public void set8D(int i, double d) {UnsafeUtils.set8d(_mem,i<<3,d);}
  public double get8D(int i) {return UnsafeUtils.get8d(_mem,i<<3);}

  @Override boolean set_impl(int i, double d) {
    UnsafeUtils.set8d(_mem,i<<3,d);
    return true;
  }
  @Override boolean set_impl(int i, float f ) {
    UnsafeUtils.set8d(_mem,i<<3,f);
    return true;
  }


  @Override boolean setNA_impl(int idx) { UnsafeUtils.set8d(_mem,(idx<<3),Double.NaN); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    //nothing to inflate - just copy
    nc.alloc_doubles(_len);
    for( int i=0; i< _len; i++ )
      nc.doubles()[i] = UnsafeUtils.get8d(_mem,(i<<3));
    nc.set_sparseLen(nc.set_len(_len));
    return nc;
  }
  // 3.3333333e33
//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }
  @Override public C8DChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>3);
    assert _mem.length == _len <<3;
    return this;
  }
}
