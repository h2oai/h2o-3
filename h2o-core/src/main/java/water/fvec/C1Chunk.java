package water.fvec;

/**
 * The empty-compression function, if all elements fit directly on UNSIGNED bytes.
 * Cannot store 0xFF, the value is a marker for N/A.
 */
public final class C1Chunk extends ByteArraySupportedChunk {
  static protected final int _OFF = 0;
  static protected final long _NA = 0xFF;
  C1Chunk(byte[] bs) { _mem=bs;}

  public int len(){return _mem.length;}

  @Override public final long at8(int i ) {
    long res = 0xFF&_mem[i+_OFF];
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override public final double atd(int i ) {
    long res = 0xFF&_mem[i+_OFF];
    return (res == _NA)?Double.NaN:res;
  }
  @Override public final boolean isNA( int i ) { return (0xFF&_mem[i+_OFF]) == _NA; }
  @Override protected boolean set_impl(int i, long l) {
    if( !(0 <= l && l < 255) ) return false;
    _mem[i+_OFF] = (byte)l;
    return true;
  }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { _mem[idx+_OFF] = (byte)_NA; return true; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = 0xFF&_mem[i+_OFF];
    v._e = 0;
    v._missing = (v._m == _NA);
    return v;
  }

  private final void addVal(int i, NewChunk nc){
    int res = 0xFF&_mem[i+_OFF];
    if( res == _NA ) nc.addNA();
    else             nc.addNum(res,0);
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    for( int i=from; i<to; i++ ) addVal(i,nc);
    return nc;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    for( int i:rows) addVal(i,nc);
    return nc;
  }

  @Override public void initFromBytes(){}

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
