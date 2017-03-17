package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'float's.
 */
public class C4FChunk extends Chunk {
  public C4FChunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>2); }
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

  @Override public NewChunk extractRows(NewChunk nc, int from, int to){
    for(int i = from; i < to; i++)
      nc.addNum(UnsafeUtils.get4f(_mem,4*i));
    return nc;
  }
  @Override public NewChunk extractRows(NewChunk nc, int... rows){
    for(int i:rows)
      nc.addNum(UnsafeUtils.get4f(_mem,4*i));
    return nc;
  }

  private final void processRow(int r, ChunkVisitor v){
    float f = UnsafeUtils.get4f(_mem,(r<<2));
    if(Float.isNaN(f)) v.addNAs(1);
    else v.addValue((double)f);
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

  // 3.3333333e33
//  public int pformat_len0() { return 14; }
//  public String pformat0() { return "% 13.7e"; }
  @Override public final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>2);
    assert _mem.length == _len <<2;
  }
  @Override public boolean hasFloat() {return true;}
}
