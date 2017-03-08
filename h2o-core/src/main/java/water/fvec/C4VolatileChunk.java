package water.fvec;

import water.*;
import water.util.UnsafeUtils;

/**
 * The empty-compression function, where data is in 'int's.
 * Can only be used locally (intentionally does not serialize).
 * Intended for temporary data which gets modified frequently.
 * Exposes data directly as int[]
 */
public class C4VolatileChunk extends Chunk {
  static protected final long _NA = Integer.MIN_VALUE;
  transient private int [] _is;

  C4VolatileChunk(int[] is ) { _is = is; _mem = new byte[0]; _start = -1; _len = is.length; }

  public boolean isVolatile() {return true;}
  public int[] getValues(){return _is;}

  @Override protected final long at8_impl( int i ) {
    long res = _is[i];
    if( res == _NA ) throw new IllegalArgumentException("at8_abs but value is missing");
    return res;
  }
  @Override protected final double atd_impl( int i ) {
    long res = _is[i];
    return res == _NA?Double.NaN:res;
  }
  @Override protected final boolean isNA_impl( int i ) { return _is[i] == _NA; }
  @Override boolean set_impl(int idx, long l) {
    if( !(Integer.MIN_VALUE < l && l <= Integer.MAX_VALUE) ) return false;
    _is[idx] = (int)l;
    return true;
  }
  @Override boolean set_impl(int i, double d) {return false; }
  @Override boolean set_impl(int i, float f ) {return false; }
  @Override boolean setNA_impl(int idx) { _is[idx] = (int)_NA; return true; }

  private final void processRow(int r, ChunkVisitor v){
    int i = UnsafeUtils.get4(_mem,(r<<2));
    if(i == _NA) v.addNAs(1);
    else v.addValue(i);
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int from, int to) {
    for(int i = from; i < to; i++) processRow(i,v);
    return v;
  }

  @Override
  public <T extends ChunkVisitor> T processRows(T v, int[] ids) {
    for(int i:ids) processRow(i,v);
    return v;
  }

  @Override public final void initFromBytes () {
    _len = _mem.length >> 2;
    _is = MemoryManager.malloc4(_len);
    for(int i = 0; i < _is.length; ++i)
      _is[i] = UnsafeUtils.get4(_mem,4*i);
    _mem = null;
  }

  @Override
  public Futures close( int cidx, Futures fs ) {
    if(chk2() != null) return chk2().close(cidx,fs);
    Value v = new Value(_vec.chunkKey(cidx),this,_len*4,Value.ICE);
    DKV.put(v._key,v,fs);
    return fs;
  }


  @Override public byte [] asBytes() {
    byte [] res = MemoryManager.malloc1(_len*4);
    for(int i = 0; i < _len; ++i)
      UnsafeUtils.set4(res,4*i,_is[i]);
    return res;
  }

  @Override public boolean hasFloat() {return false;}

//  public Futures close(int cidx, Futures fs ) { // always assume got modified
//    Value v = new Value(_vec.chunkKey(_cidx), this,this._len*4,Value.ICE);
//    DKV.put(v._key,v,fs); // Write updated chunk back into K/V
//    return fs;
//  }


}
