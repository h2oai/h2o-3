package water.fvec;

import water.*;
import water.util.UnsafeUtils;
import water.parser.ValueString;

public class CStrChunk extends Chunk {
  static final int NA = -1;
  static protected final int _OFF=4;
  private int _valstart;

  public CStrChunk() {}
  public CStrChunk(int sslen, byte[] ss, int sparseLen, int idxLen, int[] strIdx) {
    _start = -1;
    _valstart = _OFF + (idxLen<<2);
    set_len(idxLen);

    _mem = MemoryManager.malloc1(CStrChunk._OFF + idxLen*4 + sslen, false);
    UnsafeUtils.set4(_mem, 0, CStrChunk._OFF + idxLen*4); // location of start of strings

    for( int i = 0; i < sparseLen; ++i )
      UnsafeUtils.set4(_mem, CStrChunk._OFF + 4*i, strIdx[i]);
    for( int i = sparseLen; i < idxLen; ++i )  // set NAs
      UnsafeUtils.set4(_mem, CStrChunk._OFF + 4*i, -1);
    for( int i = 0; i < sslen; ++i )
      _mem[CStrChunk._OFF + idxLen*4 + i] = ss[i];
  }

  @Override public boolean setNA_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, float f) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, double d) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, long l) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public boolean set_impl(int idx, String str) { return false; }

  @Override public boolean isNA_impl(int idx) {
    int off = UnsafeUtils.get4(_mem,(idx<<2)+_OFF);
    return off == NA;
  }

  @Override public long at8_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public double atd_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
  @Override public ValueString atStr_impl(ValueString vstr, int idx) {
    int off = UnsafeUtils.get4(_mem,(idx<<2)+_OFF);
    if( off == NA ) return null;
    int len = 0;
    while( _mem[_valstart+off+len] != 0 ) len++;
    return vstr.set(_mem,_valstart+off,len);
  }

  @Override public boolean isSparse() { return false; }
  @Override public int sparseLen() { return _len; }

  @Override public CStrChunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;  _cidx = -1;
    _valstart = UnsafeUtils.get4(_mem,0);
    set_len((_valstart-_OFF)>>2);
    return this;
  }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_len(_len);
    nc.set_sparseLen(sparseLen());
    nc._is = MemoryManager.malloc4(_len);
    for( int i = 0; i < _len; i++ )
      nc._is[i] = UnsafeUtils.get4(_mem,(i<<2)+_OFF);
    nc._sslen = _mem.length - _valstart;
    nc._ss = MemoryManager.malloc1(nc._sslen);
    System.arraycopy(_mem,_valstart,nc._ss,0,nc._sslen);
    return nc;
  }
}

