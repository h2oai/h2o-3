package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

import java.util.Iterator;

public class CXDChunk extends CXIChunk {
  protected CXDChunk(int len, int valsz, byte [] buf){super(len,valsz,buf);}

  // extract fp value from an (byte)offset
  protected final double getFValue(int off){
    if(valsz() == 8) return UnsafeUtils.get8d(_mem, off + ridsz());
    throw H2O.fail();
  }

  @Override public int asSparseDoubles(double [] vals, int[] ids, double NA) {
    if(vals.length < _sparseLen) throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = 8 + _ridsz;
    if(_ridsz == 2){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get2(_mem,off) & 0xFFFF;
        double d = UnsafeUtils.get8d(_mem,off+2);
        vals[i] = Double.isNaN(d)?NA:d;
      }
    } else if(_ridsz == 4){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get4(_mem,off);
        double d = UnsafeUtils.get8d(_mem,off+4);
        vals[i] = Double.isNaN(d)?NA:d;

      }
    } else throw H2O.unimpl();
    return _sparseLen;
  }

  @Override public int asSparseDoubles(double [] vals, int[] ids) {
    if(vals.length < _sparseLen) throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = 8 + _ridsz;
    if(_ridsz == 2){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get2(_mem,off)  & 0xFFFF;
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

  @Override protected long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    double d = getFValue(off);
    if(Double.isNaN(d)) throw new IllegalArgumentException("at8_abs but value is missing");
    return (long)d;
  }
  @Override protected double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    return getFValue(off);
  }

  @Override protected boolean isNA_impl( int i ) {
    int off = findOffset(i);
    return getId(off) == i && Double.isNaN(getFValue(off));
  }

  @Override public NewChunk inflate_impl(NewChunk nc) {
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
      @Override public final long asLong(){
        double d = getFValue(_off);
        if(Double.isNaN(d)) throw new IllegalArgumentException("at8_abs but value is missing");
        return (long)d;
      }
      @Override public final double asDouble() {return getFValue(_off);}
      @Override public final boolean isNA(){
        double d = getFValue(_off);
        return Double.isNaN(d);
      }
    });
  }
  @Override
  public boolean hasFloat() {return true;}

//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }

}
