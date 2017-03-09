package water.fvec;

/**
 * The empty-compression function, if all elements fit directly on UNSIGNED bytes.
 * Cannot store 0xFF, the value is a marker for N/A.
 */
public class C1Chunk extends Chunk {
  static protected final int _OFF = 0;
  static protected final int _NA = 0xFF;
  C1Chunk(byte[] bs) { _mem=bs; _start = -1; set_len(_mem.length); }




  @Override protected final long at8_impl( int i ) {
    long res = 0xFF&_mem[i+_OFF];
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = 0xFF&_mem[i+_OFF];
    return (res == _NA)?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return (0xFF&_mem[i+_OFF]) == _NA; }
  @Override boolean set_impl(int i, long l) {
    if( !(0 <= l && l < 255) ) return false;
    _mem[i+_OFF] = (byte)l;
    return true;
  }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { _mem[idx+_OFF] = (byte)_NA; return true; }

  @Override public void initFromBytes(){
    _start = -1;  _cidx = -1;
    set_len(_mem.length);
  }

  private final void processRow(int r, ChunkVisitor v){
    int i = 0xFF&_mem[r+_OFF];
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

  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; i++) {
      int x = 0xFF&_mem[i];
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
  }

  @Override public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; i++) {
      int x = 0xFF&_mem[i];
      vals[i-from] = (x == _NA)?NA:x;
    }
    return vals;
  }

  @Override public double [] getDoubles(double [] vals, int [] ids){
    int k = 0;
    for(int i:ids) {
      int x = 0xFF&_mem[i];
      vals[k++] = (x == _NA)?Double.NaN:x;
    }
    return vals;
  }
  @Override
  public boolean hasFloat() {return false;}

}
