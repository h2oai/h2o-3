package water.fvec;

import water.AutoBuffer;
import water.Key;
import water.DKV;
import water.util.ArrayUtils;

import java.util.Arrays;
import java.util.HashMap;

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
public class CategoricalWrappedVec extends WrappedVec {
  /** List of values from underlying vector which this vector map to a new
   *  value in the union domain.  */
  int[] _map;
  int _p=0;

  /** Main constructor: convert from one categorical to another */
  public CategoricalWrappedVec(Key key, int rowLayout, String[] toDomain, Key masterVecKey) {
    super(key, rowLayout, masterVecKey);
    computeMap(masterVec().domain(),toDomain,masterVec().isBad());
    DKV.put(this);
  }

  /** Constructor just to generate the map and domain; used in tests or when
   *  mixing categorical columns */
  private CategoricalWrappedVec(Key key) { super(key, ESPC.rowLayout(key, new long[]{0}), null, null); }
  public static int[] computeMap(String[] from, String[] to) {
    Key key = Vec.newKey();
    CategoricalWrappedVec tmp = new CategoricalWrappedVec(key);
    tmp.computeMap(from, to, false);
    return tmp._map;
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    return new CategoricalWrappedChunk(masterVec().chunkForChunkIdx(cidx), this);
  }

  /** Compute a mapping from the 'from' domain to the 'to' domain.  Strings in
   *  the 'from' domain not in the 'to' domain are mapped past the end of the
   *  'to' values.  Strings in the 'to' domain not in the 'from' domain
   *  simply do not appear in the mapping.  The returned map is always the same
   *  length as the 'from' domain.  Its contents have values from both
   *  domains; the resulting domain is as big as the largest value in the map,
   *  and only has strings from the 'from' domain (which probably overlap
   *  somewhat with the 'to' domain).
   *
   *  <p> Example: from={"Blue","Red","Green"}, to={"Green","Yellow","Blue"}.<br>
   *  "Yellow" does not appear in the 'from' domain; "Red" does not appear in the 'to' domain.<br>
   *  Returned map is {2,3,0}.<br>
   *  Map length matches the 'from' domain length.<br>
   *  Largest value is 3, so the domain is size 4.<br>
   *  Domain is: {"Green","Yellow","Blue","Red"}<br>
   *  Extra values in the 'from' domain appear, in-order in the 'from' domain, at the end.
   *  @return mapping
   */
  void computeMap( String[] from, String[] to, boolean fromIsBad ) {
    // Identity? Build the cheapo non-map
    if( from==to || Arrays.equals(from,to) ) {
      _map = ArrayUtils.seq(0,to.length);
      setDomain(to);
      return;
    }

    // The source Vec does not have a domain, hence is an integer column.  The
    // to[] mapping has the set of unique numbers, we need to map from those
    // numbers to the index to the numbers.
    if( from==null ) {
      setDomain(to);
      if( fromIsBad ) { _map = new int[0]; return; }
      int min = Integer.valueOf(to[0]);
      int max = Integer.valueOf(to[to.length-1]);
      Vec mvec = masterVec();
      if( !(mvec.isInt() && mvec.min() >= min && mvec.max() <= max) )
        throw new NumberFormatException(); // Unable to figure out a valid mapping

      // FIXME this is a bit of a hack to allow adapTo calls to play nice with negative ints in the domain...
      if( Integer.valueOf(to[0]) < 0 ) {
        _p=Math.max(0,max);
        _map = new int[(_p /*positive array of values*/) + (-1*min /*negative array of values*/) + 1 /*one more to store "max" value*/];
        for(int i=0;i<to.length;++i) {
          int v = Integer.valueOf(to[i]);
          if( v < 0 ) v = -1*v+_p;
          _map[v] = i;
        }
        return;
      }

      _map = new int[max+1];
      for( int i=0; i<to.length; i++ )
        _map[Integer.valueOf(to[i])] = i;
      return;
    }

    // The desired result Vec does not have a domain, hence is a numeric
    // column.  For classification of numbers, we did an original toCategoricalVec
    // wrapping the numeric values up as Strings for the classes.  Unwind that,
    // converting numeric strings back to their original numbers.
    _map = new int[from.length];
    if( to == null ) {
      for( int i=0; i<from.length; i++ )
        _map[i] = Integer.valueOf(from[i]);
      return;
    }
    // Full string-to-string mapping
    HashMap<String,Integer> h = new HashMap<>();
    for( int i=0; i<to.length; i++ ) h.put(to[i],i);
    String[] ss = to;
    int extra = to.length;
    int actualLen = extra;
    for( int j=0; j<from.length; j++ ) {
      Integer x = h.get(from[j]);
      if( x!=null ) _map[j] = x;
      else {
        _map[j] = extra++;
        if (extra > ss.length) {
          ss = Arrays.copyOf(ss, 2*ss.length);
        }
        ss[extra-1] = from[j];
        actualLen = extra;
      }
    }
    setDomain(Arrays.copyOf(ss, actualLen));
  }

  @Override
  public Vec doCopy() {
    return new CategoricalWrappedVec(group().addVec(),_rowLayout, domain(), _masterVecKey);
  }

  public static class CategoricalWrappedChunk extends Chunk {
    public final transient Chunk _c;             // Test-set map
    final transient int[] _map;
    final transient int   _p;

    CategoricalWrappedChunk(Chunk c, CategoricalWrappedVec vec) {
      _c  = c; set_len(_c._len);
      _start = _c._start; _vec = vec; _cidx = _c._cidx;
      _map = vec._map; _p = vec._p;
    }

    // Returns the mapped value.  {@code _map} covers all the values in the
    // master Chunk, so no AIOOBE.  Missing values in the master Chunk return
    // the usual NaN.
    @Override protected double atd_impl(int idx) { return _c.isNA_impl(idx) ? Double.NaN : at8_impl(idx); }

    // Returns the mapped value.  {@code _map} covers all the values in the
    // master Chunk, so no AIOOBE.  Missing values in the master Chunk throw
    // the normal missing-value exception when loading from the master.
    @Override protected long at8_impl(int idx) {
      int at8 = (int)_c.at8_impl(idx);
      if( at8 >= 0 ) return _map[at8];
      else return _map[-1*at8+_p];
    }

    // Returns true if the masterVec is missing, false otherwise
    @Override protected boolean isNA_impl(int idx) { return _c.isNA_impl(idx); }
    @Override boolean set_impl(int idx, long l)   { return false; }
    @Override boolean set_impl(int idx, double d) { return false; }
    @Override boolean set_impl(int idx, float f)  { return false; }
    @Override boolean setNA_impl(int idx)         { return false; }
    @Override public NewChunk inflate_impl(NewChunk nc) {
      nc.set_sparseLen(nc.set_len(0));
      for( int i=0; i< _len; i++ )
        if(isNA(i))nc.addNA();
        else nc.addNum(at8(i),0);
      return nc;
    }
    @Override public AutoBuffer write_impl(AutoBuffer bb) { throw water.H2O.fail(); }
    @Override public CategoricalWrappedChunk read_impl(AutoBuffer bb)       { throw water.H2O.fail(); }
    @Override public boolean hasNA() { return false; }
  }
}
