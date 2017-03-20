package water.fvec;

import water.util.UnsafeUtils;

/**
 * The scale/bias function, where data is in SIGNED bytes before scaling.
 */
public class C2SChunk extends Chunk {
  static protected final int _OFF=8+8;
  private transient double _scale;
  public double scale() { return _scale; }
  private transient long _bias;
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

  private void processRow(int r, ChunkVisitor v){
    long res = UnsafeUtils.get2(_mem,(r<<1)+_OFF);
    if( res == C2Chunk._NA ) v.addNAs(1);
    else v.addValue((res+_bias)*_scale);
  }
  private void processRow(int r, int exp, ChunkVisitor v){
    long res = UnsafeUtils.get2(_mem,(r<<1)+_OFF);
    if( res == C2Chunk._NA ) v.addNAs(1);
    else v.addValue((res+_bias),exp);
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    if(v.expandedVals()){
      double x = Math.log10(_scale);
      int e = (int)x;
      assert x == e:"scale does not fit into int";
      for(int i = from; i < to; i++) processRow(i,e,v);
    } else
      for(int i = from; i < to; i++) processRow(i,v);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    if(v.expandedVals()){
      double x = Math.log10(_scale);
      int e = (int)x;
      assert x == e:"scale does not fit into int";
      for(int i:ids) processRow(i,e, v);
    } else
      for(int i:ids) processRow(i,v);
    return v;
  }

  @Override public byte precision() { return (byte)Math.max(-Math.log10(_scale),0); }
  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len((_mem.length-_OFF)>>1);
    _scale= UnsafeUtils.get8d(_mem,0);
    _bias = UnsafeUtils.get8 (_mem,8);
  }

  @Override public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; i++) {
      int x = UnsafeUtils.get2(_mem, _OFF + 2*i);
      vals[i-from] = (x == C2Chunk._NA)?NA:(x+_bias)*_scale;
    }
    return vals;
  }

  @Override public double [] getDoubles(double [] vals, int from, int to, double NA, double bias, double scale){
    bias = _scale*bias - _bias;
    scale = scale*_scale;
    for(int i = from; i < to; i++) {
      int x = UnsafeUtils.get2(_mem, _OFF + 2*i);
      vals[i-from] = (x == C2Chunk._NA)?NA:(x-bias)*scale;
    }
    return vals;
  }


  @Override public double [] getDoubles(double [] vals, int [] ids) {
    int k = 0;
    for(int i:ids) {
      int x = UnsafeUtils.get2(_mem, _OFF + 2*i);
      vals[k++] = (x == C2Chunk._NA)?Double.NaN:(x+_bias)*_scale;
    }
    return vals;
  }
}
