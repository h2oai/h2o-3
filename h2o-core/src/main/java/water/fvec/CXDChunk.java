package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

import java.util.Iterator;

public class CXDChunk extends CXIChunk {
  protected CXDChunk(int len, int nzs, int valsz, byte [] buf){super(len,nzs,valsz,buf);}

  // extract fp value from an (byte)offset
  protected final double getFValue(int off){
    if(valsz() == 8) return UnsafeUtils.get8d(_mem, off + ridsz());
    throw H2O.unimpl();
  }

  @Override protected long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    double d = getFValue(off);
    if(Double.isNaN(d)) throw new IllegalArgumentException("at8 but value is missing");
    return (long)d;
  }
  @Override protected double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return 0;
    return getFValue(off);
  }

  @Override protected boolean isNA_impl( int i ) {
    int off = findOffset(i);
    if(getId(off) != i)return false;
    return Double.isNaN(getFValue(off));
  }

  @Override NewChunk inflate_impl(NewChunk nc) {
    final int slen = sparseLen();
    nc.set_len(len());
    nc.set_sparseLen(slen);
    nc.alloc_doubles(slen);
    nc.alloc_indices(slen);
    int off = _OFF;
    for( int i = 0; i < slen; ++i, off += ridsz() + valsz()) {
      nc.indices()[i] = getId(off);
      nc.doubles()[i] = getFValue(off);
    }
    return nc;
  }

  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong(){
        double d = getFValue(_off);
        if(Double.isNaN(d)) throw new IllegalArgumentException("at8 but value is missing");
        return (long)d;
      }
      @Override public final double asDouble() {return getFValue(_off);}
      @Override public final boolean isNA(){
        double d = getFValue(_off);
        return Double.isNaN(d);
      }
    });
  }

//  public int pformat_len0() { return 22; }
//  public String pformat0() { return "% 21.15e"; }

}
