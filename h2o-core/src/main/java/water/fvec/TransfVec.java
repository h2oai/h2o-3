package water.fvec;

import water.AutoBuffer;
import water.Key;
import water.DKV;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.HashMap;

/** A vector transforming values of given vector according to given domain
 *  mapping - currently only used to transform Enum columns but in theory would
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
public class TransfVec extends WrappedVec {
  /** List of values from underlying vector which this vector map to a new
   *  value in the union domain.  */
  int[] _map;

  /** Main constructor: convert from one enum to another */
  public TransfVec(Key key, long[] espc, String[] toDomain, Key masterVecKey) {
    super(key, espc, masterVecKey);
    computeMap(masterVec().domain(),toDomain);
    DKV.put(this);
  }

  /** Constructor just to generate the map and domain; used in tests */
  TransfVec( String[] from, String[] to ) {
    super(Vec.VectorGroup.VG_LEN1.addVec(),new long[]{0},null,null);
    computeMap(from,to);
    DKV.put(this);
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    return new TransfChunk(masterVec().chunkForChunkIdx(cidx), this);
  }

  /** Compute a mapping from the 'from' domain to the 'to' domain.  Strings in
   *  the 'from' domain not in the 'to' domain are mapped past the end of the
   *  'from' values.  Strings in the 'to' domain not in the 'from' domain
   *  simply do not appear in the mapping.  The returned map is always the same
   *  length as the 'from' domain.  It's contents have values from both
   *  domains; the resulting domain is as big as the largest value in the map,
   *  and only has strings from the 'from' domain (which probably overlap
   *  somewhat with the 'to' domain).
   *
   *  <p> Example: from={"Blue","Red","Green"}, to={"Green","Yellow","Blue"}.<br>
   *  "Yellow" does not appear in the 'from' domain; "Red" does not appeaar in the 'to' domain.<br>
   *  Returned map is {2,3,0}.<br>
   *  Map length matches the 'from' domain length.<br>
   *  Largest value is 3, so the domain is size 4.<br>
   *  Domain is: {"Green","Blue","Yellow","Red"}<br>
   *  Extra values in the 'from' domain appear, in-order in the 'from' domain, at the end.
   *  @return mapping
   */
  void computeMap( String[] from, String[] to ) {
    if( from==null || from==to || Arrays.equals(from,to) ) {
      _map = ArrayUtils.seq(0,to.length);
      setDomain(to);
      return;
    }
    HashMap<String,Integer> h = new HashMap<>();
    for( int i=0; i<to.length; i++ ) h.put(to[i],i);
    _map = new int[from.length];
    String[] ss = to;
    int extra = to.length;
    for( int j=0; j<from.length; j++ ) {
      Integer x = h.get(from[j]);
      if( x!=null ) _map[j] = x;
      else {
        _map[j] = extra++;
        ss = Arrays.copyOf(ss,extra);
        ss[extra-1] = from[j];
      }
    }
    setDomain(ss);
  }


  static class TransfChunk extends Chunk {
    final Chunk _c;             // Test-set map
    final transient int[] _map;

    TransfChunk(Chunk c, TransfVec vec) { _c  = c; set_len(_c._len); _start = _c._start; _vec = vec; _map = vec._map; }

    // Returns the mapped value.  {@code _map} covers all the values in the
    // master Chunk, so no AIOOBE.  Missing values in the master Chunk return
    // the usual NaN.
    @Override protected double atd_impl(int idx) { return _c.isNA_impl(idx) ? Double.NaN : at8_impl(idx); }

    // Returns the mapped value.  {@code _map} covers all the values in the
    // master Chunk, so no AIOOBE.  Missing values in the master Chunk throw
    // the normal missing-value exception when loading from the master.
    @Override protected long at8_impl(int idx) { return _map[(int)_c.at8_impl(idx)]; }

    // Returns true if the masterVec is missing, false otherwise
    @Override protected boolean isNA_impl(int idx) { return _c.isNA_impl(idx); }
    @Override boolean set_impl(int idx, long l)   { return false; }
    @Override boolean set_impl(int idx, double d) { return false; }
    @Override boolean set_impl(int idx, float f)  { return false; }
    @Override boolean setNA_impl(int idx)         { return false; }
    @Override public NewChunk inflate_impl(NewChunk nc) {
      nc.set_sparseLen(nc.set_len(0));
      for( int i=0; i< _len; i++ )
        if(isNA0(i))nc.addNA();
        else nc.addNum(at80(i),0);
      return nc;
    }
    @Override public AutoBuffer write_impl(AutoBuffer bb) { throw water.H2O.fail(); }
    @Override public Chunk read_impl(AutoBuffer bb)       { throw water.H2O.fail(); }
  }
}
