package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'float's.
 */
public class C4FChunk extends Chunk {
  C4FChunk( byte[] bs ) { _mem=bs; set_len(_mem.length>>2); }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    for( int i=from; i<to; i++ ) {
      float res = UnsafeUtils.get4f(_mem,(i<<2));
      if( Float.isNaN(res) ) nc.addNum(Double.NaN);
      else nc.addNum(res);
    }
    return nc;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    for( int i:lines ) {
      float res = UnsafeUtils.get4f(_mem,(i<<2));
      if( Float.isNaN(res) ) nc.addNum(Double.NaN);
      else nc.addNum(res);
    }
    return nc;
  }

  @Override protected final long at8_impl( int i ) {
    float res = UnsafeUtils.get4f(_mem, i << 2);
    if( Float.isNaN(res) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }
  @Override protected final double atd_impl( int i ) {
    float res = UnsafeUtils.get4f(_mem,i<<2);
    return Float.isNaN(res)?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return Float.isNaN(UnsafeUtils.get4f(_mem,i<<2)); }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) {
    UnsafeUtils.set4f(_mem,i<<2,f);
    return true;
  }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set4f(_mem,(idx<<2),Float.NaN); return true; }
  // 3.3333333e33
//  public int pformat_len0() { return 14; }
//  public String pformat0() { return "% 13.7e"; }
  @Override public final void initFromBytes () {
    set_len(_mem.length>>2);
    assert _mem.length == _len <<2;
  }
  @Override public boolean hasFloat() {return true;}
}
