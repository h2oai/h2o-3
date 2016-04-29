package water.fvec;


import water.H2O;
import water.util.UnsafeUtils;

import java.util.Iterator;

//NA sparse double chunk
public class CNAXDChunk extends CNAXIChunk {
  
  protected CNAXDChunk(int len, int valsz, byte [] buf){super(len,valsz,buf);}

  // extract fp value from an (byte)offset
  protected final double getFValue(int off){
    if(valsz() == 8) return UnsafeUtils.get8d(_mem, off + ridsz());
    throw H2O.fail();
  }

  @Override protected long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx) throw new IllegalArgumentException("at8_abs but value is missing");
    double d = getFValue(off);
    return (long)d;
  }
  @Override protected double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return Double.NaN;
    return getFValue(off);
  }

  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.setSparseNA();
    nc.set_len(_len);
    nc.set_sparseLen(_sparseLen);
    nc.alloc_doubles(_sparseLen);
    nc.alloc_indices(_sparseLen);
    int off = _OFF;
    for( int i = 0; i < _sparseLen; ++i, off += ridsz() + valsz()) {
      nc.indices()[i] = getId(off);
      nc.doubles()[i] = getFValue(off);
    }
    return nc;
  }

  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong() {return (long) getFValue(_off);}
      @Override public final double asDouble() {return getFValue(_off);}
      @Override public final boolean isNA(){return false;}
    });
  }
  @Override
  public boolean hasFloat() {return true;}


  @Override public int asSparseDoubles(double [] vals, int[] ids, double NA) {
    if(vals.length < _sparseLen) throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = 8 + _ridsz;
    if(_ridsz == 2){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get2(_mem,off)  & 0xFFFF;
        ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
        vals[i] = UnsafeUtils.get8d(_mem,off+2);
      }
    } else if(_ridsz == 4){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get4(_mem,off);
        vals[i] = UnsafeUtils.get8d(_mem,off+4);
      }
    } else throw H2O.unimpl();
    return _sparseLen;
  }

}
