package water.fvec;

import water.util.UnsafeUtils;

/**
 * The constant 'long' column.
 */
public class C0LChunk extends Chunk {
  private long _con;

  public static Chunk makeConstChunk(long con){
    if(con == 0) return C0Chunk._instance;
    if(con == 1) return Const1Chunk._instance;
    return new C0LChunk(con);
  }
  private  C0LChunk(long con) {
    assert con != 0:"should use C0Chunk instead";
    assert con != 1:"should use Const1Chunk instead";
    _con = con;
  }
  @Override public boolean hasFloat() { return false; }
  @Override public boolean hasNA() { return false; }

  @Override
  public Chunk deepCopy() {return this; /* no need to copy constant chunk */ }

  @Override public final long at8(int i ) { return _con; }
  @Override public final double atd(int i ) {return _con; }
  @Override public final boolean isNA( int i ) { return false; }
  @Override protected boolean set_impl(int idx, long l) { return l==_con; }
  @Override protected boolean set_impl(int i, double d) { return d==_con; }
  @Override protected boolean set_impl(int i, float f ) { return f==_con; }
  @Override protected boolean setNA_impl(int i) { return false; }

  @Override
  public DVal getInflated(int i, DVal v) {
    v._missing = false;
    v._t = DVal.type.N;
    v._m = _con;
    v._e = 0;
    return v;
  }

  @Override
  public int len() {return 0;}

  @Override protected boolean set_impl (int idx, String str) { return false; }
  @Override double min() { return _con; }
  @Override double max() { return _con; }


  @Override public NewChunk add2Chunk(NewChunk nc, int from, int to) {return add2Chunk(nc,to-from);}
  @Override public NewChunk add2Chunk(NewChunk nc, int[] rows) { return add2Chunk(nc,rows.length);}
  private NewChunk add2Chunk(NewChunk nc, int len){
    if(_con == 0) nc.addZeros(len);
    else for(int i = 0; i < len; ++i)
        nc.addNum(_con,0);
    return nc;
  }

  @Override public int asSparseDoubles(double [] vals, int [] ids, double NA){
    for(int i = 0; i < vals.length; ++i) {
      vals[i] = _con;
      ids[i] = i;
    }
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
    for(int i = from; i < to; ++i)
      vals[i-from] = _con;
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
