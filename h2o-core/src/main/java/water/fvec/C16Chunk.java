package water.fvec;

import water.*;

public class C16Chunk extends Chunk {
  public static final long _LO_NA = Long.MAX_VALUE;
  public static final long _HI_NA = 0;
  C16Chunk( byte[] bs ) { _mem=bs; _start = -1; _len = _mem.length>>4; }
  @Override protected final long   at8_impl( int i ) { throw new IllegalArgumentException("at8 but 16-byte UUID");  }
  @Override protected final double atd_impl( int i ) { throw new IllegalArgumentException("atd but 16-byte UUID");  }
  @Override protected final boolean isNA_impl( int i ) { return UDP.get8(_mem,(i<<4))==_LO_NA && UDP.get8(_mem,(i<<4)+8)==_HI_NA; }
  @Override protected long at16l_impl(int idx) { 
    long lo = UDP.get8(_mem,(idx<<4)  );
    long hi = UDP.get8(_mem,(idx<<4)+8);
    if( lo==_LO_NA && hi==_HI_NA ) throw new IllegalArgumentException("at16 but value is missing");
    return lo;
  }
  @Override protected long at16h_impl(int idx) { 
    long lo = UDP.get8(_mem,(idx<<4)  );
    long hi = UDP.get8(_mem,(idx<<4)+8);
    if( lo==_LO_NA && hi==_HI_NA ) throw new IllegalArgumentException("at16 but value is missing");
    return hi;
  }
  @Override boolean set_impl(int idx, long l) { return false; }
  @Override boolean set_impl(int i, double d) { return false; }
  @Override boolean set_impl(int i, float f ) { return false; }
  @Override boolean setNA_impl(int idx) { UDP.set8(_mem,(idx<<4),_LO_NA); UDP.set8(_mem,(idx<<4),_HI_NA); return true; }
  @Override NewChunk inflate_impl(NewChunk nc) {
    //nothing to inflate - just copy
    nc._ls = MemoryManager.malloc8 (_len);
    nc._ds = MemoryManager.malloc8d(_len);
    nc._len = _len;
    nc._len2 = _len;
    for( int i=0; i<_len; i++ ) { //use unsafe?
      nc._ls[i] =                         UDP.get8(_mem,(i<<4)  );
      nc._ds[i] = Double.longBitsToDouble(UDP.get8(_mem,(i<<4)+8));
    }
    return nc;
  }
  @Override public AutoBuffer write_impl(AutoBuffer bb) { return bb.putA1(_mem,_mem.length); }
  @Override public C16Chunk read_impl(AutoBuffer bb) {
    _mem = bb.bufClose();
    _start = -1;
    _len = _mem.length>>4;
    assert _mem.length == _len<<4;
    return this;
  }
  @Override protected int pformat_len0() { return 36; }
}
