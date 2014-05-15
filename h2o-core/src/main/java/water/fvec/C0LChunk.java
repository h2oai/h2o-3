package water.fvec;

import java.util.Arrays;
import water.AutoBuffer;
import water.MemoryManager;
import water.UDP;

/**
 * The constant 'long' column.
 */
public class C0LChunk extends Chunk {
  static final int OFF=8+4;
  long _con;
  public C0LChunk(long con, int len) { _mem=new byte[OFF]; _start = -1; _len = len;
    _con = con;
    UDP.set8(_mem,0,con);
    UDP.set4(_mem,8,len);
  }
  @Override protected final long at8_impl( int i ) { return _con; }
  @Override protected final double atd_impl( int i ) {return _con; }
  @Override protected final boolean isNA_impl( int i ) { return false; }
  @Override boolean set_impl(int idx, long l) { return l==_con; }
  @Override boolean set_impl(int i, double d) { return d==_con; }
  @Override boolean set_impl(int i, float f ) { return f==_con; }
  @Override boolean setNA_impl(int i) { return false; }
  @Override NewChunk inflate_impl(NewChunk nc) {
    if(_con == 0) {
      nc._id = new int[0];
      nc._ls = new long[0];
      nc._xs = new int[0];
    }
    else {
      nc._ls = MemoryManager.malloc8(_len);
      Arrays.fill(nc._ls,_con);
      nc._xs = MemoryManager.malloc4(_len);
    }
    return nc;
  }
  @Override public int sparseLen(){return _con == 0?0:_len;}
  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem,_mem.length); }
  @Override public C0LChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _con = UDP.get8(_mem,0);
    _len = UDP.get4(_mem,8);
    return this;
  }
}
