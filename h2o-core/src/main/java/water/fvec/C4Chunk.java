package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'int's.
 */
public class C4Chunk extends ByteArraySupportedChunk {
  static protected final int _NA = Integer.MIN_VALUE;
  public int len(){return _mem.length >> 2;}
  C4Chunk( byte[] bs ) { _mem=bs; }
  @Override public final long at8(int i ) {
    long res = UnsafeUtils.get4(_mem,i<<2);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override public final double atd(int i ) {
    long res = UnsafeUtils.get4(_mem, i << 2);
    return res == _NA?Double.NaN:res;
  }
  @Override public final boolean isNA( int i ) { return UnsafeUtils.get4(_mem,i<<2) == _NA; }

  @Override protected boolean set_impl(int idx, long l) {
    if(l == _NA || l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) return false;
    UnsafeUtils.set4(_mem,idx<<2,(int)l);
    return true;
  }

  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set4(_mem,(idx<<2),(int)_NA); return true; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = UnsafeUtils.get4(_mem,i<<2);
    v._e = 0;
    v._missing = v._m == _NA;
    return v;
  }

  private final void addVal(int i, NewChunk nc){
    int res = UnsafeUtils.get4(_mem,(i<<2));
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
