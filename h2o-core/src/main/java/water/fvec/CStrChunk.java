package water.fvec;

import water.*;
import water.util.UnsafeUtils;
import water.parser.ValueString;

public class CStrChunk extends Chunk {
  static final int NA = -1;
  byte _strbuf[];

  public CStrChunk(byte ssbuf[], byte isbuf[], int len) {
    _mem = isbuf;
    _strbuf = ssbuf;
    set_len(_mem.length >> 2);
  }

  @Override public boolean setNA_impl(int idx) { throw new IllegalArgumentException("Only Strings allowed");}
  @Override public boolean set_impl(int idx, float f) { throw new IllegalArgumentException("Only Strings allowed");}
  @Override public boolean set_impl(int idx, double d) { throw new IllegalArgumentException("Only Strings allowed");}
  @Override public boolean set_impl(int idx, long l) { throw new IllegalArgumentException("Only Strings allowed");}
  @Override public boolean set_impl(int idx, String str) { return false; }

  @Override public boolean isNA_impl(int idx) { return false; }

  @Override public long at8_impl(int idx) { throw new IllegalArgumentException("Only Strings allowed");}
  @Override public double atd_impl(int idx) { throw new IllegalArgumentException("Only Strings allowed");}
  @Override public ValueString atStr_impl(ValueString vstr, int idx) {
    int off = UnsafeUtils.get4(_mem,idx<<2);
    if( off == NA ) return null;
    int len;
    for( len = 0; _strbuf[off+len] != 0; len++ ) ;
    return vstr.set(_strbuf,off,len);
  }

  @Override public boolean isSparse() { return false; }
  @Override public int sparseLen() { return len(); }

  @Override public AutoBuffer write_impl(AutoBuffer bb) {
    bb.putA1(_mem, _mem.length);
    return bb.putA1(_strbuf);
  }
  @Override public CStrChunk read_impl(AutoBuffer bb) {
    _mem = bb.getA1();
    _strbuf = bb.getA1();
    set_len(_strbuf.length >> 2);
    _start = -1;
    return this;
  }
  @Override NewChunk inflate_impl(NewChunk nc) {
    nc.set_len(len());
    nc.set_sparseLen(sparseLen());
    nc._ss = _strbuf;
    nc._sslen = _strbuf.length;
    nc._is = MemoryManager.malloc4(len());
    for( int i = 0; i < len(); i++ )
      nc._is[i] = UnsafeUtils.get4(_mem,i<<2);
    return nc;
  }
}

