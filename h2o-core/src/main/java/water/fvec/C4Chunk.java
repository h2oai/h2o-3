package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'int's.
 */
public class C4Chunk extends Chunk {
  static protected final long _NA = Integer.MIN_VALUE;
  C4Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>2); }
  @Override protected final long at8_impl( int i ) {
    long res = UnsafeUtils.get4(_mem,i<<2);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = UnsafeUtils.get4(_mem, i << 2);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get4(_mem,i<<2) == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Integer.MIN_VALUE < l && l <= Integer.MAX_VALUE) ) return false;
    UnsafeUtils.set4(_mem,idx<<2,(int)l);
    return true;
  }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set4(_mem,(idx<<2),(int)_NA); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(0);
    nc.set_len(0);
    final int len = _len;
    for( int i=0; i<len; i++ ) {
      int res = UnsafeUtils.get4(_mem,(i<<2));
      if( res == _NA ) nc.addNA();
      else             nc.addNum(res,0);
    }
    return nc;
  }
  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>2);
    assert _mem.length == _len <<2;
  }
  @Override public boolean hasFloat() {return false;}


  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      long res = UnsafeUtils.get4(_mem, i << 2);
      vals[i - from] = res != _NA?res:NA;
    }
    return vals;
  }
  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  @Override
  public double [] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids){
      long res = UnsafeUtils.get4(_mem,i<<2);
      vals[j++] = res != _NA?res:Double.NaN;
    }
    return vals;
  }

  @Override
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i) {
      int res = UnsafeUtils.get4(_mem, i << 2);
      vals[i - from] = res != _NA?res:NA;
    }
    return vals;
  }

}
