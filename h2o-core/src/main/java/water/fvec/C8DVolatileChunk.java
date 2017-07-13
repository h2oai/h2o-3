package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'double's.
 * Can only be used locally (intentionally does not serialize).
 * Intended for temporary data which gets modified frequently.
 * Exposes data directly as double[]
 */
public final class C8DVolatileChunk extends Chunk {
  private transient double [] _ds;
  C8DVolatileChunk(double[] ds ) {_start = -1; _len = ds.length; _ds = ds; }



  public double [] getValues(){return _ds;}
  @Override protected final long   at8_impl( int i ) {
    double res = _ds[i];
    if( Double.isNaN(res) ) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)res;
  }
  @Override protected final double   atd_impl( int i ) {
    return _ds[i] ;
  }
  @Override protected final boolean isNA_impl( int i ) { return Double.isNaN(_ds[i]); }
  @Override boolean set_impl(int idx, long l) {
    double d = l;
    if(d != l) return false;
    _ds[idx] = d;
    return true;
  }
  @Override boolean set_impl(int i, double d) {
    _ds[i] = d;
    return true;
  }
  @Override boolean set_impl(int i, float f ) {
    _ds[i] = f;
    return true;
  }
  public boolean isVolatile() {return true;}
  @Override boolean setNA_impl(int idx) { UnsafeUtils.set8d(_mem,(idx<<3),Double.NaN); return true; }

  @Override public final void initFromBytes () {
    _len = _mem.length >> 3;
    _ds = MemoryManager.malloc8d(_len);
    for(int i = 0; i < _ds.length; ++i)
      _ds[i] = UnsafeUtils.get8d(_mem,8*i);
    _mem = null;
  }

  @Override public byte [] asBytes() {
    byte [] res = MemoryManager.malloc1(_len*8);
    for(int i = 0; i < _len; ++i)
      UnsafeUtils.set8d(res,8*i,_ds[i]);
    return res;
  }

  @Override
  public Futures close( int cidx, Futures fs ) {
    if(chk2() != null) return chk2().close(cidx,fs);
    Value v = new Value(_vec.chunkKey(cidx),this,_len*8,Value.ICE);
    DKV.put(v._key,v,fs);
    return fs;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    for(int i = from; i < to; i++) v.addValue(_ds[i]);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    for(int i:ids) v.addValue(_ds[i]);
    return v;
  }

}
