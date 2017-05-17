package water.fvec;

import water.parser.BufferedString;
import water.util.UnsafeUtils;

import java.util.UUID;

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
  long at16h_impl(int idx) { throw wrongType(UUID.class, Object.class); }
  @Override protected final double atd_impl( int i ) {return _con;}
  @Override protected final boolean isNA_impl( int i ) { return Double.isNaN(_con); }
  @Override boolean set_impl(int idx, long l) { return l==_con; }
  @Override boolean set_impl(int i, double d) { return d==_con; }
  @Override boolean set_impl(int i, float f ) { return f==_con; }
  @Override boolean setNA_impl(int i) { return Double.isNaN(_con); }
  @Override double min() { return _con; }
  @Override double max() { return _con; }

  BufferedString atStr_impl(BufferedString bStr, int idx) {
    if(Double.isNaN(_con)) return null; // speciall all missing case
    return super.atStr_impl(bStr,idx);
  }
  // 3.3333333e33
//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }
  // Custom serializers: the _mem field contains ALL the fields already.
  // Init _start to -1, so we know we have not filled in other fields.
  // Leave _vec & _chk2 null, leave _len unknown.
  @Override final public void initFromBytes() {
    _start = -1;  _cidx = -1;
    _con = UnsafeUtils.get8d(_mem,0);
    set_len(UnsafeUtils.get4(_mem,8));
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
  @Override public int getSparseDoubles(double [] vals, int [] ids, double NA){
    if(_con == 0) return 0;
    double con = Double.isNaN(_con)?NA:_con;
    for(int i = 0; i < _len; ++i) {
      vals[i] = con;
      ids[i] = i;
    }
    return _len;
  }


  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to){
    if(_con == 0)
      v.addZeros(to-from);
    else if(Double.isNaN(_con))
      v.addNAs(to-from);
    else for(int i = from; i < to; i++)
        v.addValue(_con);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int [] ids){
    if(_con == 0)
      v.addZeros(ids.length);
    else if(Double.isNaN(_con))
      v.addNAs(ids.length);
    else for(int i = 0; i < ids.length; i++)
        v.addValue(_con);
    return v;
  }

}
