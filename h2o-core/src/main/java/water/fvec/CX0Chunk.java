package water.fvec;

import java.util.Arrays;
import java.util.Iterator;

/** specialized subtype of SPARSE chunk for boolean (bitvector); no NAs.  contains just a list of rows that are non-zero. */
public final class CX0Chunk extends CXIChunk {
  // Sparse constructor
  protected CX0Chunk(int len, int nzs, byte [] buf){super(len,nzs,0,buf);}

  @Override protected final long at8_impl(int idx) {return getId(findOffset(idx)) == idx?1:0;}
  @Override protected final double atd_impl(int idx) { return at8_impl(idx); }
  @Override protected final boolean isNA_impl( int i ) { return false; }
  @Override double min() { return 0; }
  @Override double max() { return 1; }
  @Override public boolean hasNA() { return false; }

  @Override public NewChunk inflate_impl(NewChunk nc) {
    final int slen = sparseLen();
    nc.set_len(_len);
    nc.set_sparseLen(slen);
    nc.alloc_mantissa(slen);
    Arrays.fill(nc.mantissa(),1);
    nc.alloc_exponent(slen);
    nc.alloc_indices(slen);
    nonzeros(nc.indices());
    return nc;
  }

  public Iterator<Value> values(){
    return new SparseIterator(new Value(){
      @Override public final long asLong(){return 1;}
      @Override public final double asDouble() { return 1;}
      @Override public final boolean isNA(){ return false;}
    });
  }
}
