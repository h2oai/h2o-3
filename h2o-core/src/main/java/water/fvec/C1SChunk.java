package water.fvec;

import water.util.PrettyPrint;
import water.util.UnsafeUtils;

import java.util.Arrays;

/**
 * The scale/bias function, where data is in SIGNED bytes before scaling.
 */
public class C1SChunk extends ByteArraySupportedChunk {
  static protected final int _OFF=8+8;
  private transient double _scale;
  public double scale() { return _scale; }
  private transient long _bias;
  @Override public boolean hasFloat(){ return _scale != (long)_scale; }
  C1SChunk( byte[] bs, long bias, double scale ) { _mem=bs;
    _bias = bias; _scale = scale;
    UnsafeUtils.set8d(_mem, 0, scale);
    UnsafeUtils.set8 (_mem,8,bias );
  }
  @Override public final long at8(int i ) {
    long res = 0xFF&_mem[i+_OFF];
    if( res == C1Chunk._NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)((res+_bias)*_scale);
  }
  @Override public final double atd(int i ) {
    long res = 0xFF&_mem[i+_OFF];
    return (res == C1Chunk._NA)?Double.NaN:(res+_bias)*_scale;
  }
  @Override public final boolean isNA( int i ) { return (0xFF&_mem[i+_OFF]) == C1Chunk._NA; }
  @Override protected boolean set_impl(int i, long l) {
    long res = (long)(l/_scale)-_bias; // Compressed value
    double d = (res+_bias)*_scale;     // Reverse it
    if( (long)d != l ) return false;   // Does not reverse cleanly?
    if( !(0 <= res && res < 255) ) return false; // Out-o-range for a byte array
    _mem[i+_OFF] = (byte)res;
    return true;
  }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { _mem[idx+_OFF] = (byte)C1Chunk._NA; return true; }


  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = 0xFF&_mem[i+_OFF];
    v._e = Arrays.binarySearch(PrettyPrint.powers10,_scale);
    assert v._e >= 0;
    v._e -= 10;
    v._missing = v._m == C1Chunk._NA;
    return v;
  }

  //public int pformat_len0() { return hasFloat() ? pformat_len0(_scale,3) : super.pformat_len0(); }
  //public String  pformat0() { return hasFloat() ? "% 8.2e" : super.pformat0(); }
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
      long res = 0xFF & _mem[_OFF+i];
      vals[i-from] = res != C1Chunk._NA?(res + _bias)*_scale:NA;
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
      long res = 0xFF&_mem[_OFF+i];
      vals[j++] = res != C1Chunk._NA?(res + _bias)*_scale:Double.NaN;
    }
    return vals;
  }

  public int len(){return _mem.length - _OFF;}

}
