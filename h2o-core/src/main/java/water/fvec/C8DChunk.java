package water.fvec;

import water.MemoryManager;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'double's.
 */
public class C8DChunk extends ByteArraySupportedChunk {

  C8DChunk( byte[] bs ) { _mem=bs; }
  C8DChunk( double[] ds ) {
    _mem=MemoryManager.malloc1(ds.length <<3);
    for(int i = 0; i < ds.length; ++i)
      UnsafeUtils.set8d(_mem,i<<3,ds[i]);
  }
  public int len(){return _mem.length >> 3;}

  @Override public final long at8(int i ) {
    double res = UnsafeUtils.get8d(_mem, i << 3);
    if( Double.isNaN(res) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }
  @Override public final double atd(int i ) { return              UnsafeUtils.get8d(_mem,i<<3) ; }
  @Override public final boolean isNA( int i ) { return Double.isNaN(UnsafeUtils.get8d(_mem,i<<3)); }
  @Override protected boolean set_impl(int idx, long l) { return false; }

  /**
   * Fast explicit set for double.
   * @param i
   * @param d
   */
  public void set8D(int i, double d) {UnsafeUtils.set8d(_mem,i<<3,d);}
  public double get8D(int i) {return UnsafeUtils.get8d(_mem,i<<3);}

  @Override protected boolean set_impl(int i, double d) {
    UnsafeUtils.set8d(_mem,i<<3,d);
    return true;
  }
  @Override protected boolean set_impl(int i, float f ) {
    UnsafeUtils.set8d(_mem,i<<3,f);
    return true;
  }


  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set8d(_mem,(idx<<3),Double.NaN); return true; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.D;
    v._d = UnsafeUtils.get8d(_mem,(i<<3));
    return v;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    for( int i=from; i<to; i++ ) nc.addNum(UnsafeUtils.get8d(_mem,(i<<3)));
    return nc;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    for( int i:rows) nc.addNum(UnsafeUtils.get8d(_mem,(i<<3)));
    return nc;
  }
  // 3.3333333e33
//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }
  @Override public final void initFromBytes () {}

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

}
