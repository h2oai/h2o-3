package water.fvec;

import water.AutoBuffer;
import water.MemoryManager;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'double's.
 */
public class C8DChunk extends Chunk {
  C8DChunk( byte[] bs ) { _mem=bs; set_len(_mem.length>>3); }

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

  // 3.3333333e33
//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }
  @Override public final void initFromBytes () {
    set_len(_mem.length>>3);
    assert _mem.length == _len <<3;
  }

  @Override
  public double [] getDoubles(double [] vals, int from, int to){
    for(int i = from; i < to; ++i)
      vals[i - from] = UnsafeUtils.get8d(_mem, i << 3);
    return vals;
  }

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      double d = UnsafeUtils.get8d(_mem, i << 3);
      vals[i - from] = Double.isNaN(d)?NA:d;
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
    for(int i:ids) vals[j++] = UnsafeUtils.get8d(_mem,i<<3);
    return vals;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    for( int i=from; i< to; i++ )
      nc.addNum(UnsafeUtils.get8d(_mem,(i<<3)));
    return nc;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    nc.alloc_doubles(_len);
    for( int i:lines)
      nc.addNum(UnsafeUtils.get8d(_mem,(i<<3)));
    return nc;
  }

}
