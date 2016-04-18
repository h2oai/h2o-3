package water.fvec;

import java.util.Iterator;

// NA sparse integer chunk 
public class CNAXIChunk extends CXIChunk {
  
  protected CNAXIChunk(int len, int valsz, byte [] buf){super(len, valsz, buf);}
  
  @Override public final boolean isSparseZero() {return false;}
  @Override public int sparseLenZero() {return _len;}
  @Override public int nextNZ(int rid){ return rid + 1;}
  @Override public int nonzeros(int [] res) {
    for( int i = 0; i < _len; ++i) res[i] = i;
    return _len;
  }

  @Override public final boolean isSparseNA() {return true;}
  @Override public int sparseLenNA() {return _sparseLen;}
  @Override public final int nextNNA(int rid){
    final int off = rid == -1?_OFF:findOffset(rid);
    int x = getId(off);
    if(x > rid)return x;
    if(off < _mem.length - ridsz() - valsz())
      return getId(off + ridsz() + valsz());
    return _len;
  }
  /** Fills in a provided (recycled/reused) temp array of the NNAs indices, and
   *  returns the count of them.  Array must be large enough. */
  @Override public final int nonnas(int [] arr){
    int off = _OFF;
    final int inc = valsz() + ridsz();
    for(int i = 0; i < _sparseLen; ++i, off += inc) arr[i] = getId(off);
    return _sparseLen;
  }

  @Override protected long at8_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx) throw new IllegalArgumentException("at8_abs but value is missing");
    return getIValue(off);
  }
  @Override protected double atd_impl(int idx) {
    int off = findOffset(idx);
    if(getId(off) != idx)return Double.NaN;
    return getIValue(off);
  }
  
  @Override protected boolean isNA_impl( int i ) {
    int off = findOffset(i);
    return getId(off) != i;
  }

  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.setSparseNA();
    nc.alloc_mantissa(_sparseLen);
    nc.alloc_exponent(_sparseLen);
    nc.alloc_indices(_sparseLen);
    int off = _OFF;
    for( int i = 0; i < _sparseLen; ++i, off += ridsz() + valsz()) {
      long v = getIValue(off);
      int id = getId(off);
      nc.addNAs(id-nc._len);
      nc.addNum(v,0);
    }
    nc.set_len(_len);
    return nc;
  }

  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong() {return getIValue(_off);}
      @Override public final double asDouble() {return getIValue(_off);}
      @Override public final boolean isNA() {return false;}
    });
  }
}
