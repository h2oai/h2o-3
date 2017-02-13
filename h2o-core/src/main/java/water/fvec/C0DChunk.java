package water.fvec;

import water.util.UnsafeUtils;

import java.util.Arrays;

/**
 * The constant 'double' column.
 */
public class C0DChunk extends Chunk {
  private double _con;

  public static Chunk makeConstChunk(double con){
    if(con == 0) return C0Chunk._instance;
    if(con == 1) return Const1Chunk._instance;
    if(Double.isNaN(con)) return CNAChunk._instance;
    return new C0DChunk(con);
  }

  private  C0DChunk(double con) {
    assert con != 0:"should use C0Chunk for 0";
    assert con != 1:"should use Const1Chunk for 0";
    assert !Double.isNaN(con):"Should use CNAChunk";
    _con = con;
  }
  @Override public boolean hasFloat() { return (long)_con != _con; }

  @Override
  public Chunk deepCopy() {return this; /* no need to clone const chunk */}

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

  @Override
  public DVal getInflated(int i, DVal v) {
    v._t = DVal.type.D;
    v._d = _con;
    return v;
  }

  @Override
  public int len() {return 0;}

  @Override double min() { return _con; }
  @Override double max() { return _con; }



  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {return add2Chunk(nc,to-from);}
  @Override public NewChunk add2Chunk(NewChunk nc, int[] rows) { return add2Chunk(nc,rows.length);}
  private NewChunk add2Chunk(NewChunk nc, int len){
    if(_con == 0) nc.addZeros(len);
    else if(Double.isNaN(_con)) nc.addNAs(len);
    else for(int i = 0; i < len; ++i)
      nc.addNum(_con);
    return nc;
  }


  @Override public boolean isSparseZero(){return false;}
  @Override public boolean isSparseNA(){return false;}
  @Override public int nextNNA(int rid) {return rid+1;}

  @Override public int asSparseDoubles(double [] vals, int [] ids, double NA){
    Arrays.fill(vals,_con);
    for(int i = 0; i < vals.length; ++i)
      ids[i] = i;
    return vals.length;
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
