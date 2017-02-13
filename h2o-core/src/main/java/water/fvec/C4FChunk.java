package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'float's.
 */
public class C4FChunk extends ByteArraySupportedChunk {
  C4FChunk( byte[] bs ) { _mem=bs;  }
  public int len(){return _mem.length >> 2;}



  @Override public final long at8(int i ) {
    float res = UnsafeUtils.get4f(_mem, i << 2);
    if( Float.isNaN(res) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }
  @Override public final double atd(int i ) {
    float res = UnsafeUtils.get4f(_mem,i<<2);
    return Float.isNaN(res)?Double.NaN:res;
  }
  @Override public final boolean isNA( int i ) { return Float.isNaN(UnsafeUtils.get4f(_mem,i<<2)); }
  @Override protected boolean set_impl(int idx, long l) { return false; }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) {
    UnsafeUtils.set4f(_mem,i<<2,f);
    return true;
  }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.D;
    float res = UnsafeUtils.get4f(_mem,(i<<2));
    if( Float.isNaN(res) ) v._d = Double.NaN;
    else v._d = res;
    return v;
  }

  private final void addVal(int i, NewChunk nc){
    float res = UnsafeUtils.get4f(_mem,(i<<2));
    if( Float.isNaN(res) ) nc.addNum(Double.NaN);
    else nc.addNum(res);
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    for( int i=from; i<to; i++ ) addVal(i,nc);
    return nc;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    for( int i:rows) addVal(i,nc);
    return nc;
  }

  // 3.3333333e33
//  public int pformat_len0() { return 14; }
//  public String pformat0() { return "% 13.7e"; }
  @Override public final void initFromBytes () {}
  @Override public boolean hasFloat() {return true;}
}
