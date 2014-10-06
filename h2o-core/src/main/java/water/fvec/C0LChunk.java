package water.fvec;

import java.util.Arrays;
import water.AutoBuffer;
import water.util.UnsafeUtils;

/**
 * The constant 'long' column.
 */
public class C0LChunk extends Chunk {
  protected static final int _OFF=8+4;
  private long _con;
  public C0LChunk(long con, int len) { _mem=new byte[_OFF]; _start = -1; set_len(len);
    _con = con;
    UnsafeUtils.set8(_mem, 0, con);
    UnsafeUtils.set4(_mem,8,len);
  }
  @Override protected final long at8_impl( int i ) { return _con; }
  @Override protected final double atd_impl( int i ) {return _con; }
  @Override protected final boolean isNA_impl( int i ) { return false; }
  @Override boolean set_impl(int idx, long l) { return l==_con; }
  @Override boolean set_impl(int i, double d) { return d==_con; }
  @Override boolean set_impl(int i, float f ) { return f==_con; }
  @Override boolean setNA_impl(int i) { return false; }
  @Override NewChunk inflate_impl(NewChunk nc) {
    if(_con != 0) {
      nc.alloc_mantissa(_len);
      Arrays.fill(nc.mantissa(), _con);
      nc.alloc_exponent(_len);
      nc.set_len(nc.set_sparseLen(_len));
    } else {
      nc.set_len(nc.set_sparseLen(0));
      nc.set_sparse(0);
      nc.addZeros(_len);
      assert(nc.sparseLen() == 0);
    }
    return nc;
  }
  @Override public int sparseLen(){return _con == 0?0: _len;}
  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem,_mem.length); }
  @Override public C0LChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _con = UnsafeUtils.get8(_mem,0);
    set_len(UnsafeUtils.get4(_mem,8));
    return this;
  }
}
