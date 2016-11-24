package water.fvec;

import water.*;
import water.util.UnsafeUtils;

public class C16Chunk extends Chunk {
  static final long _LO_NA = Long.MIN_VALUE;
  static final long _HI_NA = 0;
  C16Chunk( byte[] bs ) { _mem=bs; _start = -1; set_len(_mem.length>>4); }
  @Override protected final long   at8_impl( int i ) { throw new IllegalArgumentException("at8_abs but 16-byte UUID");  }
  @Override protected final double atd_impl( int i ) { throw new IllegalArgumentException("atd but 16-byte UUID");  }

  @Override protected final boolean isNA_impl( int i ) { return isNA(loAt(i), hiAt(i)); }
  public static boolean isNA(long lo, long hi) { return lo ==_LO_NA && hi ==_HI_NA; }
  private long loAt(int idx) { return UnsafeUtils.get8(_mem, idx*16); }
  private long hiAt(int idx) { return UnsafeUtils.get8(_mem, idx*16+8); }

  @Override protected long at16l_impl(int idx) {
    long lo = loAt(idx);
    if (lo == _LO_NA && hiAt(idx) == _HI_NA) {
      throw new IllegalArgumentException("at16l but value is missing at " + idx);
    }
    return lo;
  }
  @Override protected long at16h_impl(int idx) {
    long hi = hiAt(idx);
    if (hi == _HI_NA && loAt(idx) == _LO_NA) {
      throw new IllegalArgumentException("at16h but value is missing at " + idx);
    }
    return hi;
  }
  @Override boolean set_impl(int i, long lo, long hi) {
    if (isNA(lo, hi)) throw new IllegalArgumentException("Illegal uid value");
    UnsafeUtils.set8(_mem, i*16,     lo);
    UnsafeUtils.set8(_mem, i*16 + 8, hi);
    return true;
  }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { return set_impl(idx, _LO_NA, _HI_NA); }
  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_len(nc.set_sparseLen(0));

    for( int i=0; i< _len; i++ ) {
      long lo = loAt(i);
      long hi = hiAt(i);
      if(isNA(lo, hi)) nc.addNA();
      else
        nc.addUUID(lo, hi);
    }
    return nc;
  }
  @Override protected final void initFromBytes () {
    _start = -1;  _cidx = -1;
    set_len(_mem.length>>4);
    assert _mem.length == _len * 16;
  }
//  @Override protected int pformat_len0() { return 36; }
}
