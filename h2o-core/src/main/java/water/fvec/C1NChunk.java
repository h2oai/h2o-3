package water.fvec;

import water.*;

/**
 * The empty-compression function, if all elements fit directly on UNSIGNED bytes.
 * [In particular, this is the compression style for data read in from files.]
 */
public class C1NChunk extends Chunk {
  protected static final int _OFF=0;
  public C1NChunk(byte[] bs) { _mem=bs; _start = -1; set_len(_mem.length); }
  @Override protected final long   at8_impl( int i ) { return 0xFF&_mem[i]; }
  @Override protected final double atd_impl( int i ) { return 0xFF&_mem[i]; }
  @Override protected final boolean isNA_impl( int i ) { return false; }
  @Override boolean set_impl(int i, long l  ) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { return false; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.alloc_exponent(_len);
    nc.alloc_mantissa(_len);
    for( int i=0; i< _len; i++ )
      nc.addNum(0xFF&_mem[i+_OFF],0);
    nc.set_sparseLen(nc.set_len(_len));
    return nc;
  }
  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  @Override protected final void initFromBytes () {
    _start = -1;
    _cidx = -1;
    set_len(_mem.length);
  }
  @Override public boolean hasFloat() {return false;}
  @Override public boolean hasNA() { return false; }

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
  public int [] getIntegers(int [] vals, int from, int to, int NA){
    for(int i = from; i < to; ++i)
      vals[i - from] = 0xFF & _mem[i];
    return vals;
  }

}
