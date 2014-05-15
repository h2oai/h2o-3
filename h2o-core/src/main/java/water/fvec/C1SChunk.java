package water.fvec;

import java.util.Arrays;
import water.*;

/**
 * The scale/bias function, where data is in SIGNED bytes before scaling.
 */
public class C1SChunk extends Chunk {
  static final int OFF=8+4;
  public double _scale;
  int _bias;
  C1SChunk( byte[] bs, int bias, double scale ) { _mem=bs; _start = -1; _len = _mem.length-OFF;
    _bias = bias; _scale = scale;
    UDP.set8d(_mem,0,scale);
    UDP.set4 (_mem,8,bias );
  }
  @Override protected final long at8_impl( int i ) {
    long res = 0xFF&_mem[i+OFF];
    if( res == C1Chunk._NA ) throw new IllegalArgumentException("at8 but value is missing");
    return (long)((res+_bias)*_scale);
  }
  @Override protected final double atd_impl( int i ) {
    long res = 0xFF&_mem[i+OFF];
    return (res == C1Chunk._NA)?Double.NaN:(res+_bias)*_scale;
  }
  @Override protected final boolean isNA_impl( int i ) { return (0xFF&_mem[i+OFF]) == C1Chunk._NA; }
  @Override boolean set_impl(int i, long l) {
    long res = (long)(l/_scale)-_bias; // Compressed value
    double d = (res+_bias)*_scale;     // Reverse it
    if( (long)d != l ) return false;   // Does not reverse cleanly?
    if( !(0 <= res && res < 255) ) return false; // Out-o-range for a byte array
    _mem[i+OFF] = (byte)res;
    return true;
  }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { _mem[idx+OFF] = (byte)C1Chunk._NA; return true; }
  @Override NewChunk inflate_impl(NewChunk nc) {
    double dx = Math.log10(_scale);
    assert water.util.PrettyPrint.fitsIntoInt(dx);
    Arrays.fill(nc._xs = MemoryManager.malloc4(_len), (int)dx);
    nc._ls = MemoryManager.malloc8(_len);
    for( int i=0; i<_len; i++ ) {
      int res = 0xFF&_mem[i+OFF];
      if( res == C1Chunk._NA ) nc._xs[i] = Integer.MIN_VALUE;
      else                     nc._ls[i] = res+_bias;
    }
    return nc;
  }
  //public int pformat_len0() { return hasFloat() ? pformat_len0(_scale,3) : super.pformat_len0(); }
  //public String  pformat0() { return hasFloat() ? "% 8.2e" : super.pformat0(); }
  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem,_mem.length); }
  @Override public C1SChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _len = _mem.length-OFF;
    _scale= UDP.get8d(_mem,0);
    _bias = UDP.get4 (_mem,8);
    return this;
  }
}
