package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The scale/bias function, where data is in SIGNED bytes before scaling.
 */
public class C2SChunk extends Chunk {
  static protected final int _OFF=8+8;
  private double _scale;
  public double scale() { return _scale; }
  private long _bias;
  public boolean hasFloat(){ return _scale != (long)_scale; }
  C2SChunk( byte[] bs, long bias, double scale ) { _mem=bs; _start = -1; set_len((_mem.length-_OFF)>>1);
    _bias = bias; _scale = scale;
    UnsafeUtils.set8d(_mem, 0, scale);
    UnsafeUtils.set8 (_mem,8,bias );
  }
  @Override protected final long at8_impl( int i ) {
    long res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    if( res == C2Chunk._NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)((res + _bias)*_scale);
  }
  @Override protected final double atd_impl( int i ) {
    long res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    return (res == C2Chunk._NA)?Double.NaN:(res + _bias)*_scale;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get2(_mem,(i<<1)+_OFF) == C2Chunk._NA; }
  @Override boolean set_impl(int idx, long l) {
    long res = (long)(l/_scale)-_bias; // Compressed value
    double d = (res+_bias)*_scale;     // Reverse it
    if( (long)d != l ) return false;   // Does not reverse cleanly?
    if( !(Short.MIN_VALUE < res && res <= Short.MAX_VALUE) ) return false; // Out-o-range for a short array
    UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)res);
    return true;
  }
  @Override boolean set_impl(int i, double d) {
    short s = (short)((d/_scale)-_bias);
    if( s == C2Chunk._NA ) return false;
    double d2 = (s+_bias)*_scale;
    if( d!=d2 ) return false;
    UnsafeUtils.set2(_mem,(i<<1)+_OFF,s);
    return true;
  }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)C2Chunk._NA); return true; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    double dx = Math.log10(_scale);
    assert water.util.PrettyPrint.fitsIntoInt(dx);
    nc.set_sparseLen(0);
    nc.set_len(0);
    final int len = _len;
    for( int i=0; i<len; i++ ) {
      int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
      if( res == C2Chunk._NA ) nc.addNA();
      else nc.addNum((res+_bias),(int)dx);
    }
    return nc;
  }
//  public int pformat_len0() {
//    throw H2O.unimpl();
//    //if( _scale==0.01 ) return 5;
//    //return hasFloat() ? pformat_len0(_scale,5) : super.pformat_len0();
//  }
//  public String  pformat0() {
//    throw H2O.unimpl();
//    //if( _scale==0.01 ) return "%7.2f";
//    //return hasFloat() ? "% 10.4e" : super.pformat0();
//  }
  @Override public byte precision() { return (byte)Math.max(-Math.log10(_scale),0); }
  @Override public C2SChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    set_len((_mem.length-_OFF)>>1);
    _scale= UnsafeUtils.get8d(_mem,0);
    _bias = UnsafeUtils.get8 (_mem,8);
    return this;
  }
}
