package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in shorts.
 */
public class C2Chunk extends Chunk {
  static protected final int _NA = Short.MIN_VALUE;
  static protected final int _OFF=0;

  C2Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>1); }
  @Override protected final long at8_impl( int i ) {
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    int res = UnsafeUtils.get2(_mem,(i<<1)+_OFF);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get2(_mem,(i<<1)+_OFF) == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Short.MIN_VALUE < l && l <= Short.MAX_VALUE) ) return false;
    UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)l);
    return true;
  }
  @Override boolean set_impl(int idx, double d) {
    if( Double.isNaN(d) ) return setNA_impl(idx);
    long l = (long)d;
    return l == d && set_impl(idx, l);
  }
  @Override boolean set_impl(int i, float f ) { return set_impl(i,(double)f); }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set2(_mem,(idx<<1)+_OFF,(short)_NA); return true; }

  private final void processRow(int r, ChunkVisitor v){
    int i = UnsafeUtils.get2(_mem,(r<<1)+_OFF);
    if(i == _NA) v.addNAs(1);
    else v.addValue(i);
  }


  @Override public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; i++) {
      int x = UnsafeUtils.get2(_mem, 2*i);
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
  }

  @Override public double [] getDoubles(double [] vals, int [] ids){
    int k = 0;
    for(int i:ids) {
      int x = UnsafeUtils.get2(_mem, 2*i);
      vals[k++] = (x == _NA)?Double.NaN:x;
    }
    return vals;
  }

  @Override
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; i++) {
      int x = UnsafeUtils.get2(_mem, 2*i);
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
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
    set_len(_mem.length>>1);
    assert _mem.length == _len <<1;
  }
  @Override
  public boolean hasFloat() {return false;}

}
