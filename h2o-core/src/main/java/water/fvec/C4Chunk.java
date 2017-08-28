package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'int's.
 */
public class C4Chunk extends Chunk {
  static protected final int _NA = Integer.MIN_VALUE;
  C4Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>2); }
  @Override protected final long at8_impl( int i ) {
    long res = UnsafeUtils.get4(_mem,i<<2);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = UnsafeUtils.get4(_mem, i << 2);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get4(_mem,i<<2) == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Integer.MIN_VALUE < l && l <= Integer.MAX_VALUE) ) return false;
    UnsafeUtils.set4(_mem,idx<<2,(int)l);
    return true;
  }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set4(_mem,(idx<<2),(int)_NA); return true; }


  private final void processRow(int r, ChunkVisitor v){
    int i = UnsafeUtils.get4(_mem,(r<<2));
    if(i == _NA) v.addNAs(1);
    else v.addValue(i);
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    for(int i = from; i < to; i++) processRow(i,v);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    for(int i:ids) processRow(i,v);
    return v;
  }

  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>2);
    assert _mem.length == _len <<2;
  }
  @Override public boolean hasFloat() {return false;}

  @Override public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; i++) {
      int x = UnsafeUtils.get4(_mem, 4*i);
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
  }
  @Override public double [] getDoubles(double [] vals, int [] ids){
    int k = 0;
    for(int i:ids) {
      int x = UnsafeUtils.get4(_mem, 4*i);
      vals[k++] = (x == _NA)?Double.NaN:x;
    }
    return vals;
  }

  @Override public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; i++) {
      int x = UnsafeUtils.get4(_mem, 4*i);
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
  }

}
