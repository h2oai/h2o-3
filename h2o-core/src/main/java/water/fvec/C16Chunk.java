package water.fvec;

import water.*;
import water.util.UnsafeUtils;

public class C16Chunk extends Chunk {
  public static final long _LO_NA = Long.MIN_VALUE;
  public static final long _HI_NA = 0;
  C16Chunk( byte[] bs ) {
    _mem=bs;
    _achunk = null;
    set_len(_mem.length>>4);
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int from, int to) {
    for( int i=from; i< to; i++ ) {
      long lo = UnsafeUtils.get8(_mem,(i<<4)  );
      long hi = UnsafeUtils.get8(_mem,(i << 4) + 8);
      if(lo == _LO_NA && hi == _HI_NA)
        nc.addNA();
      else
        nc.addUUID(lo, hi);
    }
    return nc;
  }

  @Override
  public NewChunk add2NewChunk_impl(NewChunk nc, int[] lines) {
    for( int i: lines) {
      long lo = UnsafeUtils.get8(_mem,(i<<4)  );
      long hi = UnsafeUtils.get8(_mem,(i << 4) + 8);
      if(lo == _LO_NA && hi == _HI_NA)
        nc.addNA();
      else
        nc.addUUID(lo, hi);
    }
    return nc;
  }

  @Override
  public final long   at8_impl(int i) { throw new IllegalArgumentException("at8_abs but 16-byte UUID");  }
  @Override
  public final double atd_impl(int i) { throw new IllegalArgumentException("atd but 16-byte UUID");  }
  @Override
  public final boolean isNA_impl(int i) { return UnsafeUtils.get8(_mem,(i<<4))==_LO_NA && UnsafeUtils.get8(_mem,(i<<4)+8)==_HI_NA; }
  @Override protected long at16l_impl(int idx) { 
    long lo = UnsafeUtils.get8(_mem,(idx<<4)  );
    long hi = UnsafeUtils.get8(_mem,(idx<<4)+8);
    if( lo==_LO_NA && hi==_HI_NA ) throw new IllegalArgumentException("at16 but value is missing");
    return lo;
  }
  @Override protected long at16h_impl(int idx) { 
    long lo = UnsafeUtils.get8(_mem,(idx<<4)  );
    long hi = UnsafeUtils.get8(_mem,(idx<<4)+8);
    if( lo==_LO_NA && hi==_HI_NA ) throw new IllegalArgumentException("at16 but value is missing");
    return hi;
  }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set8(_mem, (idx << 4), _LO_NA); UnsafeUtils.set8(_mem,(idx<<4),_HI_NA); return true; }

  @Override protected final void initFromBytes () {
    _vidx = -1;
    _achunk = null;
    set_len(_mem.length>>4);
    assert _mem.length == _len <<4;
  }
//  @Override protected int pformat_len0() { return 36; }
}
