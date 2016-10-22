package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'long's.
 */
public class C8Chunk extends Chunk {
  protected static final long _NA = Long.MIN_VALUE;
  C8Chunk( byte[] bs ) { _mem=bs;_len = (_mem.length>>3); }
  @Override public final long at8(int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override public final double atd(int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    return res == _NA?Double.NaN:res;
  }
  @Override public final boolean isNA( int i ) { return UnsafeUtils.get8(_mem, i << 3)==_NA; }
  @Override protected boolean set_impl(int idx, long l) { return false; }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set8(_mem,(idx<<3),_NA); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(nc._len = 0);
    for( int i=0; i< _len; i++ )
      if(isNA(i))nc.addNA();
      else nc.addNum(at8(i),0);
    return nc;
  }
  @Override public final void initFromBytes () {
    _len = (_mem.length>>3);
    assert _mem.length == _len <<3;
  }
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
      long res = UnsafeUtils.get8(_mem, i << 3);;
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
  public double[] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids) {
      long res = UnsafeUtils.get8(_mem,i<<3);
      vals[j++] = res != _NA?res:Double.NaN;
    }
    return vals;
  }

}
