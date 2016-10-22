package water.fvec;

import water.util.UnsafeUtils;

import java.util.Arrays;

/**
 * The constant 'double' column.
 */
public class C0DChunk extends Chunk {
  private static final int _OFF=8+4;
  private double _con;
  public C0DChunk(double con, int len) {
    _len = len;
    _mem=new byte[_OFF];
    _con = con;
    UnsafeUtils.set8d(_mem, 0, con);
    UnsafeUtils.set4(_mem,8,len);
  }
  @Override public boolean hasFloat() { return true; }
  @Override public final long at8(int i ) {
    if( Double.isNaN(_con) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)_con;          // Possible silent truncation
  }
  @Override public final double atd(int i ) {return _con;}
  @Override public final boolean isNA( int i ) { return Double.isNaN(_con); }
  @Override protected boolean set_impl(int idx, long l) { return l==_con; }
  @Override protected boolean set_impl(int i, double d) { return d==_con; }
  @Override protected boolean set_impl(int i, float f ) { return f==_con; }
  @Override protected boolean setNA_impl(int i) { return Double.isNaN(_con); }
  @Override double min() { return _con; }
  @Override double max() { return _con; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    if(Double.isNaN(_con)) {
      nc.set_sparseLen(nc._len = 0);//so that addNAs(_len) can add _len
      nc.set_sparse(0, NewChunk.Compress.NA); //so that sparseNA() is true in addNAs()
      nc.addNAs(_len);
    } else if (_con == 0) {
      nc._len = (nc.set_sparseLen(0)); //so that addZeros(_len) can add _len
      nc.set_sparse(0, NewChunk.Compress.ZERO);//so that sparseZero() is true in addZeros()
      nc.addZeros(_len);
    } else {
      nc.alloc_doubles(_len);
      Arrays.fill(nc.doubles(), _con);
      nc._len = (nc.set_sparseLen(_len));
    }
    return nc;
  }
  // 3.3333333e33
//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }
  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  @Override final public void initFromBytes() {
    _con = UnsafeUtils.get8d(_mem,0);
    _len = (UnsafeUtils.get4(_mem,8));
  }

  @Override public boolean isSparseZero(){return _con == 0;}
  @Override public int sparseLenZero() {return  _con ==0?0:_len;}
  @Override public int nextNZ(int rid) {return _con==0?_len:rid+1;}
  @Override public int nonzeros(int [] arr) {
    if (_con == 0) return 0;
    for (int i = 0; i < _len; ++i) arr[i] = i;
    return _len;
  }
  
  @Override public boolean isSparseNA(){return Double.isNaN(_con);}
  @Override public int sparseLenNA() {return  Double.isNaN(_con)?0:_len;}
  @Override public int nextNNA(int rid) {return Double.isNaN(_con)?_len:rid+1;}
  @Override public int nonnas(int [] arr) {
    if (Double.isNaN(_con)) return 0;
    for (int i = 0; i < _len; ++i) arr[i] = i;
    return _len;
  }
  @Override public int asSparseDoubles(double [] vals, int [] ids, double NA){
    if(_con == 0) return 0;
    double con = Double.isNaN(_con)?NA:_con;
    for(int i = 0; i < _len; ++i) {
      vals[i] = con;
      ids[i] = i;
    }
    return _len;
  }


  /**
   * Dense bulk interface, fetch values from the given range
   * @param vals
   * @param from
   * @param to
   */
  @Override
  public double [] getDoubles(double [] vals, int from, int to, double NA){
    double con = Double.isNaN(_con)?NA:_con;
    for(int i = from; i < to; ++i)
      vals[i-from] = con;
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
    for(int i:ids) vals[j++] = _con;
    return vals;
  }

}
