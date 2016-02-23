package water.fvec;

import water.AutoBuffer;
import water.util.UnsafeUtils;

import java.util.Arrays;

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
  @Override public boolean hasFloat() { return false; }
  @Override public boolean hasNA() { return false; }
  @Override protected final long at8_impl( int i ) { return _con; }
  @Override protected final double atd_impl( int i ) {return _con; }
  @Override protected final boolean isNA_impl( int i ) { return false; }
  @Override boolean set_impl(int idx, long l) { return l==_con; }
  @Override boolean set_impl(int i, double d) { return d==_con; }
  @Override boolean set_impl(int i, float f ) { return f==_con; }
  @Override boolean setNA_impl(int i) { return false; }
  @Override boolean set_impl (int idx, String str) { return false; }
  @Override double min() { return _con; }
  @Override double max() { return _con; }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    if(_con == 0) {
      nc.set_len(nc.set_sparseLen(0)); //so that addZeros(_len) can add _len
      nc.set_sparse(0, NewChunk.Compress.ZERO);//so that sparse() is true in addZeros()
      nc.addZeros(_len);
    } else {
      nc.alloc_mantissa(_len);
      Arrays.fill(nc.mantissa(), _con);
      nc.alloc_exponent(_len);
      nc.set_len(nc.set_sparseLen(_len));
    }
    return nc;
  }
  @Override public C0LChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    _con = UnsafeUtils.get8(_mem,0);
    set_len(UnsafeUtils.get4(_mem,8));
    return this;
  }
  @Override public boolean isSparseZero(){return _con == 0;}
  @Override public int sparseLenZero(){return _con == 0?0: _len;}
  @Override public int nextNZ(int rid){return _con == 0?_len:rid+1;}
  @Override public int nonzeros(int [] arr) {
    if (_con == 0) return 0;
    for (int i = 0; i < _len; ++i) arr[i] = i;
    return _len;
  }
}
