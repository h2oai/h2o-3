package water.fvec;

import water.H2O;
import water.util.UnsafeUtils;

import java.util.Arrays;
import java.util.Iterator;

/** specialized subtype of SPARSE chunk for boolean (bitvector); no NAs.  contains just a list of rows that are non-zero. */
public final class CX0Chunk extends CXIChunk {
  // Sparse constructor
  protected CX0Chunk(int len, byte [] buf){super(len,0,buf);}

  @Override protected final long at8_impl(int idx) {return getId(findOffset(idx)) == idx?1:0;}
  @Override protected final double atd_impl(int idx) { return at8_impl(idx); }
  @Override protected final boolean isNA_impl( int i ) { return false; }
  @Override double min() { return 0; }
  @Override double max() { return 1; }
  @Override public boolean hasNA() { return false; }

  @Override public int asSparseDoubles(double [] vals, int[] ids) {
    if(vals.length != _len) throw new IllegalArgumentException();
    int off = _OFF;
    final int inc = _ridsz;
    if(_ridsz == 2){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get2(_mem,off);
        vals[i] = 1;
      }
    } else if(_ridsz == 4){
      for (int i = 0; i < _sparseLen; ++i, off += inc) {
        ids[i] = UnsafeUtils.get4(_mem,off);
        vals[i] = 1;
      }
    } else throw H2O.unimpl();
    return len();
  }

  @Override public NewChunk inflate_impl(NewChunk nc) {
    nc.set_len(_len);
    nc.set_sparseLen(_sparseLen);
    nc.alloc_mantissa(_sparseLen);
    Arrays.fill(nc.mantissa(),1);
    nc.alloc_exponent(_sparseLen);
    nc.alloc_indices(_sparseLen);
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
