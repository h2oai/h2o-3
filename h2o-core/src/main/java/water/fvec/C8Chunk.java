package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'long's.
 */
public class C8Chunk extends Chunk {
  protected static final long _NA = Long.MIN_VALUE;
  C8Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>3); }
  @Override protected final long at8_impl( int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return UnsafeUtils.get8(_mem, i << 3)==_NA; }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set8(_mem,(idx<<3),_NA); return true; }

  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>3);
    assert _mem.length == _len <<3;
  }
  @Override
  public boolean hasFloat() {return false;}

  private final void processRow(int r, ChunkVisitor v){
    long l = UnsafeUtils.get8(_mem,(r<<3));
    if(l == _NA) v.addNAs(1);
    else v.addValue(l);
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

  @Override public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; i++) {
      long x = UnsafeUtils.get8(_mem, 8*i);
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
  }
  @Override public double [] getDoubles(double [] vals, int [] ids){
    int k = 0;
    for(int i:ids) {
      long x = UnsafeUtils.get8(_mem, 8*i);
      vals[k++] = (x == _NA)?Double.NaN:x;
    }
    return vals;
  }
}
