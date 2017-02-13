package water.fvec;

import water.util.PrettyPrint;
import water.util.UnsafeUtils;

import java.util.Arrays;

/**
 * The scale/bias function, where data is in SIGNED bytes before scaling.
 */
public class C4SChunk extends ByteArraySupportedChunk {
  static private final long _NA = Integer.MIN_VALUE;
  static protected final int _OFF=8+8;
  private transient double _scale;
  public double scale() { return _scale; }
  private transient long _bias;
  @Override public boolean hasFloat(){ return _scale != (long)_scale; }
  C4SChunk( byte[] bs, long bias, double scale ) { _mem=bs;
    _bias = bias; _scale = scale;
    UnsafeUtils.set8d(_mem,0,scale);
    UnsafeUtils.set8 (_mem,8,bias );
  }
  @Override public final long at8(int i ) {
    long res = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)((res + _bias)*_scale);
  }
  @Override public final double atd(int i ) {
    long res = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
    return (res == _NA)?Double.NaN:(res + _bias)*_scale;
  }
  @Override public final boolean isNA( int i ) { return UnsafeUtils.get4(_mem,(i<<2)+_OFF) == _NA; }
  @Override protected boolean set_impl(int idx, long l) {
    long res = (long)(l/_scale)-_bias; // Compressed value
    double d = (res+_bias)*_scale;     // Reverse it
    if( (long)d != l ) return false;   // Does not reverse cleanly?
    if( !(Integer.MIN_VALUE < res && res <= Integer.MAX_VALUE) ) return false; // Out-o-range for a int array
    UnsafeUtils.set4(_mem,(idx<<2)+_OFF,(int)res);
    return true;
  }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set4(_mem,(idx<<2)+_OFF,(int)_NA); return true; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = UnsafeUtils.get4(_mem,(i<<2)+_OFF);;
    v._e = dx();
    v._missing = v._m == _NA;
    return v;
  }


  private int dx() {
    int dx = Arrays.binarySearch(PrettyPrint.powers10, _scale);
    assert dx >= 0;
    return dx - 10;
  }

  private final void addVal(int i, NewChunk nc, int dx){
    int res = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
    if( res == _NA ) nc.addNA();
    else nc.addNum(res+_bias,(int)dx);
  }


  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    int dx = dx();
    for( int i=from; i<to; i++ ) addVal(i,nc,dx);
    return nc;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    int dx = dx();
    for( int i:rows) addVal(i,nc,dx);
    return nc;
  }


//  public int pformat_len0() { return pformat_len0(_scale,5); }
//  public String pformat0() { return "% 10.4e"; }
  @Override public byte precision() { return (byte)Math.max(-Math.log10(_scale),0); }
  @Override public final void initFromBytes () {
    _scale= UnsafeUtils.get8d(_mem,0);
    _bias = UnsafeUtils.get8 (_mem,8);
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
      long res = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
      vals[i-from] = res != C4Chunk._NA?(res + _bias)*_scale:NA;
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
    for(int i:ids) {
      long res = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
      vals[j++] = res != C4Chunk._NA?(res + _bias)*_scale:Double.NaN;
    }
    return vals;
  }

  public int len(){return (_mem.length - _OFF) >> 2;}
}
