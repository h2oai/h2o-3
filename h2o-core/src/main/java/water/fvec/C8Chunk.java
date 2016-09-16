package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'long's.
 */
public class C8Chunk extends Chunk {
  static protected final int _OFF = 0;
  protected static final long _NA = Long.MIN_VALUE;
  C8Chunk( byte[] bs ) { _mem=bs; }
  @Override
  public final long at8_impl(int i) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override
  public final double atd_impl(int i) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    return res == _NA?Double.NaN:res;
  }
  @Override
  public final boolean isNA_impl(int i) { return UnsafeUtils.get8(_mem, i << 3)==_NA; }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set8(_mem,(idx<<3),_NA); return true; }
  @Override public final void initFromBytes () {}
  @Override
  public boolean hasFloat() {return false;}


  @Override
  public int len() { return (_mem.length - _OFF) >> 3;}

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

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    for( int i=from; i< to; i++ )
      if(isNA_impl(i))nc.addNA();
      else nc.addNum(at8_impl(i),0);
    return nc;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    for( int i:lines)
      if(isNA_impl(i))nc.addNA();
      else nc.addNum(at8_impl(i),0);
    return nc;
  }

}
