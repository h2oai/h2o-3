package water.fvec;

import water.AutoBuffer;
import water.util.UnsafeUtils;

/**
 * The constant 'double' column.
 */
public class C0DChunk extends Chunk {
  private static final int _OFF=8+4;
  private double _con;
  public C0DChunk(double con, int len) {
    _start = -1;
    set_len(len);
    _mem=new byte[_OFF];
    _con = con;
    UnsafeUtils.set8d(_mem, 0, con);
    UnsafeUtils.set4(_mem,8,len);
  }
  @Override public boolean hasFloat() { return true; }
  @Override protected final long at8_impl( int i ) {
    if( Double.isNaN(_con) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)_con;          // Possible silent truncation
  }
  @Override protected final double atd_impl( int i ) {return _con;}
  @Override protected final boolean isNA_impl( int i ) { return Double.isNaN(_con); }
  @Override boolean set_impl(int idx, long l) { return l==_con; }
  @Override boolean set_impl(int i, double d) { return d==_con; }
  @Override boolean set_impl(int i, float f ) { return f==_con; }
  @Override boolean setNA_impl(int i) { return Double.isNaN(_con); }
  @Override double min() { return _con; }
  @Override double max() { return _con; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_sparseLen(nc.set_len(0));
    if(_con == 0) {
      nc.addZeros(_len);
    } else {
      for (int i=0; i<_len; ++i) nc.addNum(_con);
    }
    return nc;
  }
  // 3.3333333e33
//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }
  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  @Override final public C0DChunk read_impl(AutoBuffer ab) {
    _mem = ab.bufClose();
    _start = -1;  _cidx = -1;
    _con = UnsafeUtils.get8d(_mem,0);
    set_len(UnsafeUtils.get4(_mem,8));
    return this;
  }

  @Override public int nextNZ(int rid) {
    return _con == 0?_len:rid+1;
  }
  @Override public boolean isSparse(){return _con == 0;}
}
