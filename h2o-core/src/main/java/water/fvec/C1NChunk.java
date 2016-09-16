package water.fvec;

import water.*;

/**
 * The empty-compression function, if all elements fit directly on UNSIGNED bytes.
 * [In particular, this is the compression style for data read in from files.]
 */
public class C1NChunk extends Chunk {
  protected static final int _OFF=0;
  public C1NChunk(byte[] bs) { _mem=bs; }
  @Override
  public final long   at8_impl(int i) { return 0xFF&_mem[i]; }
  @Override
  public final double atd_impl(int i) { return 0xFF&_mem[i]; }
  @Override
  public final boolean isNA_impl(int i) { return false; }
  @Override boolean set_impl(int i, long l  ) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { return false; }

  @Override
  protected void initFromBytes() {}


  @Override public boolean hasFloat() {return false;}
  @Override public boolean hasNA() { return false; }

  @Override
  public int len() {return _mem.length - _OFF;}

  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i)
      vals[i-from] = 0xFF&_mem[i];
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
    for(int i:ids) vals[j++] = 0xFF&_mem[i];
    return vals;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    for( int i=from; i< to; i++ )
      nc.addNum(0xFF&_mem[i+_OFF],0);
    return nc;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    for( int i:lines)
      nc.addNum(0xFF&_mem[i+_OFF],0);
    return nc;
  }

  @Override
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i)
      vals[i - from] = 0xFF & _mem[i];
    return vals;
  }

}
