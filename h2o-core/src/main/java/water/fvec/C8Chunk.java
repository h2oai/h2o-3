package water.fvec;

import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'long's.
 */
public class C8Chunk extends ByteArraySupportedChunk {
  protected static final long _NA = Long.MIN_VALUE;
  C8Chunk( byte[] bs ) { _mem=bs;}
  public int len(){return _mem.length >> 3;}
  @Override public final long at8(int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override public final double atd(int i ) {
    long res = UnsafeUtils.get8(_mem,i<<3);
    return res == _NA?Double.NaN:res;
  }
  @Override public final boolean isNA( int i ) { return UnsafeUtils.get8(_mem, i << 3)==_NA; }
  @Override protected boolean set_impl(int idx, long l) { return false; }
  @Override protected boolean set_impl(int i, double d) { return false; }
  @Override protected boolean set_impl(int i, float f ) { return false; }
  @Override protected boolean setNA_impl(int idx) { UnsafeUtils.set8(_mem,(idx<<3),_NA); return true; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.N;
    v._m = UnsafeUtils.get8(_mem,i<<3);
    v._e = 0;
    v._missing = v._m == _NA;
    return v;
  }

  private final void addVal(int i, NewChunk nc){
    if(isNA(i))nc.addNA();
    else nc.addNum(at8(i),0);
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {
    for( int i=from; i<to; i++ ) addVal(i,nc);
    return nc;
  }

  @Override public NewChunk add2Chunk(NewChunk nc, int [] rows) {
    for( int i:rows) addVal(i,nc);
    return nc;
  }

  @Override public final void initFromBytes () {}
  @Override
  public boolean hasFloat() {return false;}



  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double[] getDoubles(double [] vals, int from, int to, double NA){
    for(int i = from; i < to; ++i) {
      long res = UnsafeUtils.get8(_mem, i << 3);;
      vals[i - from] = res != _NA?res:NA;
    }
    return vals;
  }
  /**
   * Dense bulk interface, fetch values from the given ids
   * @param vals
   * @param ids
   */
  @Override
  public double[] getDoubles(double [] vals, int [] ids){
    int j = 0;
    for(int i:ids) {
      long res = UnsafeUtils.get8(_mem,i<<3);
      vals[j++] = res != _NA?res:Double.NaN;
    }
    return vals;
  }

  @Override
  public SparseNum nextNZ(SparseNum sv) {
    if (sv._off == -1) sv._off = 0;
    if (sv._off == _mem.length) {
      sv._id = sv._len;
      sv._val = Double.NaN;
      sv._isLong = false;
    } else {
      sv._lval = UnsafeUtils.get8(_mem, sv._off);
      sv._val = sv._lval == C8Chunk._NA?Double.NaN:(double)sv._lval;
      sv._isLong = true;
      sv._off += 8;
      sv._id++;
    }
    return sv;
  }

}
