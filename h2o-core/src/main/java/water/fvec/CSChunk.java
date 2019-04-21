package water.fvec;

import water.util.PrettyPrint;
import water.util.UnsafeUtils;

/**
 * Created by tomas on 8/14/17.
 *
 * Chunk storing 1/2/4 byte values with an offset and/or scale.
 * Used for fixed point decimal numbers or scaled/offseted integers.
 *
 * value(i) = (stored_bytes(i) + bias)*scale;  if scale > 1 or scale == 1/x and (stored_bytes(i) + bias)*(1/x) == (stored_bytes(i) + bias)/x for all i-s
 * or
 * value(i) = (stored_bytes(i) + bias)/scale ; otherwise
 *
 */
public abstract class CSChunk extends Chunk {
  static protected final int _OFF=8+4+4;
  private transient double _scale;
  private transient long _bias;
  private transient boolean _isDecimal;

  CSChunk( byte[] bs, long bias, int scale, int szLog) {
    _mem = bs;
    _start = -1;
    set_len((_mem.length - _OFF) >> szLog);
    _bias = bias;
    UnsafeUtils.set8(_mem, 0, bias);
    UnsafeUtils.set4(_mem, 8, scale);
    _scale = PrettyPrint.pow10(1,scale);
    UnsafeUtils.set4(_mem,12,szLog);
  }

  protected void setDecimal(){
    _isDecimal = true;
    _scale = PrettyPrint.pow10(1,-UnsafeUtils.get4(_mem,8));
    UnsafeUtils.set4(_mem,12,-UnsafeUtils.get4(_mem,12)-1);
  }
  private int getSzLog(){
    int x = UnsafeUtils.get4(_mem,12);
    return x < 0?-x-1:x;
  }
  public final double scale() { return _isDecimal?1.0/_scale:_scale; }

  @Override public final byte precision() {
    return (byte)Math.max(UnsafeUtils.get4(_mem,8),0);
  }

  protected final double getD(int x, int NA){return getD(x,NA,Double.NaN);}

  protected final double getD(int x, int NA, double naImpute){
    return x == NA?naImpute:_isDecimal?(_bias + x)/_scale:(_bias + x)*_scale;
  }

  protected final long get8(int x) { return (_bias + x)*(long)(_scale); }

  @Override public final boolean hasFloat(){ return _isDecimal || _scale < 1; }

  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len((_mem.length-_OFF) >> getSzLog());
    _bias = UnsafeUtils.get8 (_mem,0);
    int x = UnsafeUtils.get4(_mem,8);
    int szLog = UnsafeUtils.get4(_mem,12);
    _isDecimal = szLog < 0;
    _scale = PrettyPrint.pow10(1,_isDecimal?-x:x);
  }

  @Override protected long at8_impl( int i ) {
    double res = atd_impl(i); // note: |mantissa| <= 4B => double is ok
    if(Double.isNaN(res)) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }


  @Override public final boolean set_impl(int idx, long l) {
    double d = (double)l;
    if(d != l) return false;
    return set_impl(idx,d);
  }

  @Override public final boolean set_impl(int idx, float f) {
    return set_impl(idx,(double)f);
  }

  protected final int getScaledValue(double d, int NA){
    assert !Double.isNaN(d):"NaN should be handled separately";
    return (int)((_isDecimal?d*_scale:(d/_scale))-_bias);
  }

  @Override
  public final <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    if(v.expandedVals()){
      processRows2(v,from,to,_bias,UnsafeUtils.get4(_mem,8));
    } else
      processRows2(v,from,to);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    if(v.expandedVals()){
      processRows2(v,ids,_bias,UnsafeUtils.get4(_mem,8));
    } else
      processRows2(v,ids);
    return v;
  }
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int from, int to, long bias, int exp) ;
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int from, int to);
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int [] ids, long bias, int exp) ;
  protected abstract <T extends ChunkVisitor> T processRows2(T v, int [] ids);
}
