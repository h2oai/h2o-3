package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in shorts.
 */
public class C2Chunk extends ByteArraySupportedChunk {
  static protected final long _NA = Short.MIN_VALUE;
  static protected final int _OFF=0;
  public int len(){return _mem.length >> 1;}
  C2Chunk( byte[] bs ) { _mem=bs; }
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
  @Override protected boolean set_impl(int i, float f ) { return set_impl(i,(double)f); }
  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)_NA); return true; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    v._e = 0;
    v._missing = v._m == _NA;
    return v;
  }

  private final void addVal(int i, NewChunk nc){
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    if( res == _NA ) nc.addNA();
    else             nc.addNum(res,0);
  }
  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    for( int i=from; i<to; i++ ) addVal(i,nc);
    return nc;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    for( int i:rows) addVal(i,nc);
    return nc;
  }
  @Override public final void initFromBytes () {}
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
