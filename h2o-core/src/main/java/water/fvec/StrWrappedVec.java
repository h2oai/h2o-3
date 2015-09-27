package water.fvec;

import water.AutoBuffer;
import water.Key;
import water.DKV;
import water.parser.BufferedString;

/** A vector transforming values of given vector according to given domain
 *  mapping - currently only used to transform categorical columns but in theory would
 *  work for any dense-packed Int column.  Expected usage is to map from a new
 *  dataset to the domain-mapping expected by a model (which will match the
 *  dataset it was trained on).
 *
 *  <p>The Vector's domain is the union of the Test and Train domains.
 *
 *  <p>The mapping is defined by int[] array, size is input Test.domain.length.
 *  Contents refer to values in the Train.domain.  Extra values in the Test
 *  domain are sorted after the Train.domain - so mapped values have to be
 *  range-checked (note that returning some flag for NA, say -1, would also
 *  need to be checked for).
 */
public class StrWrappedVec extends WrappedVec {
  /** Main constructor: convert from categorical to string */
  public StrWrappedVec(Key key, long[] espc, Key masterVecKey) {
    super(key, espc, masterVecKey);
    _type = T_STR;
    DKV.put(this);
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    return new StrWrappedChunk(masterVec().chunkForChunkIdx(cidx), this);
  }

  static class StrWrappedChunk extends Chunk {
    final Chunk _c;             // Test-set map

    StrWrappedChunk(Chunk c, StrWrappedVec vec) {
      _c  = c; set_len(_c._len); _start = _c._start; _vec = vec; _cidx = _c._cidx;
    }

    @Override public double atd_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public long at8_impl(int idx) { throw new IllegalArgumentException("Operation not allowed on string vector.");}
    @Override public BufferedString atStr_impl(BufferedString vstr, int idx) {
      return isNA_impl(idx) ? null : vstr.setTo(((StrWrappedVec)_vec).masterVec().factor(_c.at8_impl(idx)));
    }

    @Override protected boolean isNA_impl(int idx) { return _c.isNA_impl(idx); }
    @Override boolean setNA_impl(int idx)         { throw new IllegalArgumentException("Operation not allowed on string vector."); }
    @Override boolean set_impl(int idx, long l)   { throw new IllegalArgumentException("Operation not allowed on string vector."); }
    @Override boolean set_impl(int idx, double d) { throw new IllegalArgumentException("Operation not allowed on string vector."); }
    @Override boolean set_impl(int idx, float f)  { throw new IllegalArgumentException("Operation not allowed on string vector."); }
    @Override boolean set_impl(int idx, String str) { return false; }

    @Override public NewChunk inflate_impl(NewChunk nc) {
      nc.set_sparseLen(nc.set_len(0));
      for( int i=0; i< _len; i++ )
        if(isNA(i))nc.addNA();
        else nc.addNum(at8(i),0);
      return nc;
    }
    @Override public AutoBuffer write_impl(AutoBuffer bb) { throw water.H2O.fail(); }
    @Override public Chunk read_impl(AutoBuffer bb)       { throw water.H2O.fail(); }
  }
}
