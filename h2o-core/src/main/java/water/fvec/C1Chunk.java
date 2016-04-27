package water.fvec;

import water.*;

/**
 * The empty-compression function, if all elements fit directly on UNSIGNED bytes.
 * Cannot store 0xFF, the value is a marker for N/A.
 */
public class C1Chunk extends Chunk {
  static protected final int _OFF = 0;
  static protected final long _NA = 0xFF;
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
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(0);
    nc.set_len(0);
    final int len = _len;
    for( int i=0; i<len; i++ ) {
      int res = 0xFF&_mem[i+_OFF];
      if( res == _NA ) nc.addNA();
      else             nc.addNum(res,0);
    }
    return nc;
  }
  @Override public void initFromBytes(){
    _start = -1;  _cidx = -1;
    set_len(_mem.length);
  }
  @Override
  public boolean hasFloat() {return false;}


  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      long res = 0xFF & _mem[i];
      vals[i-from] = res != _NA?res:NA;
    }
    return vals;
  }
  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  @Override
  public double [] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids) {
      long res = 0xFF&_mem[i];
      vals[j++] = res != _NA?res:Double.NaN;
    }
    return vals;
  }

  @Override
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i) {
      int res = 0xFF & _mem[i];
      vals[i - from] = res != _NA?res:NA;
    }
    return vals;
  }

}
