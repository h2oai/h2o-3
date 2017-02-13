package water.fvec;

/**
 * The empty-compression function, if all elements fit directly on UNSIGNED bytes.
 * [In particular, this is the compression style for data read in from files.]
 */
public class C1NChunk extends ByteArraySupportedChunk {
  protected static final int _OFF=0;
  public C1NChunk(byte[] bs) { _mem=bs;}
  @Override public final long at8(int i ) { return 0xFF&_mem[i]; }
  @Override public final double atd(int i ) { return 0xFF&_mem[i]; }
  @Override public final boolean isNA( int i ) { return false; }
  @Override protected boolean set_impl(int i, long l  ) { return false; }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { return false; }

  public int len(){return _mem.length;}
  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = 0xFF&_mem[i];
    v._e = 0;
    v._missing = false;
    return v;
  }


  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  @Override protected final void initFromBytes () {}

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
