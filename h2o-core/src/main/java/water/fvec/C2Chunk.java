package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in shorts.
 */
public class C2Chunk extends Chunk {
  static protected final long _NA = Short.MIN_VALUE;
  static protected final int _OFF=0;
  C2Chunk( byte[] bs ) { _mem=bs; _len = _mem.length>>1; }
  @Override public final long at8(int i ) {
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override public final double atd(int i ) {
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    return res == _NA?Double.NaN:res;
  }
  @Override public final boolean isNA( int i ) { return UnsafeUtils.get2(_mem,(i<<1)+_OFF) == _NA; }
  @Override protected boolean set_impl(int idx, long l) {
    if( !(Short.MIN_VALUE < l && l <= Short.MAX_VALUE) ) return false;
    UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)l);
    return true;
  }
  @Override protected boolean set_impl(int idx, double d) {
    if( Double.isNaN(d) ) return setNA_impl(idx);
    long l = (long)d;
    return l == d && set_impl(idx, l);
  }
  @Override protected boolean set_impl(int i, float f ) { return set_impl(i,(double)f); }
  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)_NA); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(0);
    final int len = _len;
    for( int i=0; i<len; i++ ) {
      int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
      if( res == _NA ) nc.addNA();
      else             nc.addNum(res,0);
    }
    return nc;
  }
  @Override public final void initFromBytes () {_len = _mem.length>>1;}
  @Override
  public boolean hasFloat() {return false;}

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double[] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      long res = UnsafeUtils.get2(_mem, i << 1);;
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
      long res = UnsafeUtils.get2(_mem,i<<1);
      vals[j++] = res != _NA?res:Double.NaN;
    }
    return vals;
  }

  @Override
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i) {
      int res = UnsafeUtils.get2(_mem, i << 1);
      vals[i - from] = res != _NA?res:NA;
    }
    return vals;
  }

}
