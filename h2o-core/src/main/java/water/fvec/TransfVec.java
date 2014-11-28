package water.fvec;

import water.AutoBuffer;
import water.DKV;
import water.Key;

import java.util.Arrays;
import java.util.Comparator;

/** A vector transforming values of given vector according to given domain
 *  mapping - currently only used to transform Enum columns but in theory would
 *  work for any dense-packed Int column.  Expected usage is to map from a new
 *  dataset to the domain-mapping expected by a model (which will match the
 *  dataset it was trained on).
 *
 *  <p>The mapping is defined by int[] array, size is input Test.domain.length.
 *  Contents refer to values in the Train.domain.  Extra values in the Test
 *  domain are sorted after the Train.domain - so mapped values have to be
 *  range-checked (note that returning some flag for NA, say -1, would also
 *  need to be checked for).
 *
 *  <p>The Vector's domain is the union of the Test and Train domains.
 */
public class TransfVec extends WrappedVec {
  /** List of values from underlying vector which this vector map to a new
   *  value in the union domain.  */
  private final int[] _values;

  public TransfVec(Key masterVecKey, Key key, long[] espc, String[] domain, int[] values) {
    super(masterVecKey, key, espc, domain);
    _values = values;
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    return new TransfChunk(masterVec().chunkForChunkIdx(cidx), this);
  }

  static abstract class TransfChunk extends Chunk {
    protected static final long MISSING_VALUE = -1L;
    final Chunk _c;
    final int[] _values;

    protected TransfChunk(Chunk c, TransfVec vec) { _c  = c; set_len(_c._len); _start = _c._start; _vec = vec; }

    @Override protected double atd_impl(int idx) { double d = 0; return _c.isNA0(idx) ? Double.NaN : ( (d=at8_impl(idx)) == MISSING_VALUE ? Double.NaN : d ) ;  }
    @Override protected boolean isNA_impl(int idx) {
      if (_c.isNA_impl(idx)) return true;
      return at8_impl(idx) == MISSING_VALUE; // this case covers situation when there is no mapping
    }

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
    @Override public AutoBuffer write_impl(AutoBuffer bb) { throw new UnsupportedOperationException(); }
    @Override public Chunk read_impl(AutoBuffer bb)       { throw new UnsupportedOperationException(); }


    public FlatTransfChunk(Chunk c, TransfVec vec) {
      super(c,vec);
      assert vec._indexes == null : "TransfChunk requires NULL indexing array.";
      _values = vec._values;
    }

    @Override protected long at8_impl(int idx) { return get(_c.at8_impl(idx)); }

    private long get(long val) {
      int indx = -1;
      return (indx = Arrays.binarySearch(_values, (int)val)) < 0 ? MISSING_VALUE : indx ;
    }
  }

//  /** Compose this vector with given transformation. Always return a new vector */
//  public Vec compose(int[][] transfMap, String[] domain) { return compose(this, transfMap, domain, true);  }
//
//  /**
//   * Compose given origVector with given transformation. Always returns a new vector.
//   * Original vector is kept if keepOrig is true.
//   * @param origVec
//   * @param transfMap
//   * @param keepOrig
//   * @return a new instance of {@link TransfVec} composing transformation of origVector and tranfsMap
//   */
//  public static Vec compose(TransfVec origVec, int[][] transfMap, String[] domain, boolean keepOrig) {
//    // Do a mapping from INT -> ENUM -> this vector ENUM
//    int[][] domMap = compose(new int[][] {origVec._values, origVec._indexes }, transfMap);
//    Vec result = origVec.masterVec().makeTransf(domMap[0], domMap[1], domain);
//    if (!keepOrig) DKV.remove(origVec._key);
//    return result;
//  }
//
//  static int[][] compose(int[][] first, int[][] second) {
//    int[] firstDom = first[0];
//    int[] firstRan = first[1];  // flat transformation
//    int[] secondDom = second[0];
//    int[] secondRan = second[1];
//
//    boolean[] filter = new boolean[firstDom.length]; int fcnt = 0;
//    int[] resDom = firstDom.clone();
//    int[] resRan = firstRan!=null ? firstRan.clone() : new int[firstDom.length];
//    for (int i=0; i<resDom.length; i++) {
//      int v = firstRan!=null ? firstRan[i] : i; // resulting value
//      int vi = Arrays.binarySearch(secondDom, v);
//      // Do not be too strict in composition assert vi >=0 : "Trying to compose two incompatible transformation: first=" + Arrays.deepToString(first) + ", second=" + Arrays.deepToString(second);
//      if (vi<0) {
//        filter[i] = true;
//        fcnt++;
//      } else
//        resRan[i] = secondRan!=null ? secondRan[vi] : vi;
//    }
//    return new int[][] { filter(resDom,filter,fcnt), filter(resRan,filter,fcnt) };
//  }
//  private static int[] filter(int[] values, boolean[] filter, int fcnt) {
//    assert filter.length == values.length : "Values should have same length as filter!";
//    assert filter.length - fcnt >= 0 : "Cannot filter more values then legth of filter vector!";
//    if (fcnt==0) return values;
//    int[] result = new int[filter.length - fcnt];
//    int c = 0;
//    for (int i=0; i<values.length; i++) {
//      if (!filter[i]) result[c++] = values[i];
//    }
//    return result;
//  }
//
//  public static int[][] pack(int[] values, boolean[] usemap) {
//    assert values.length == usemap.length : "Cannot pack the map according given use map!";
//    int cnt = 0;
//    for (boolean anUsemap : usemap) cnt += anUsemap ? 1 : 0;
//    int[] pvals = new int[cnt]; // only used values
//    int[] pindx = new int[cnt]; // indexes of used values
//    int index = 0;
//    for (int i=0; i<usemap.length; i++) {
//      if (usemap[i]) {
//        pvals[index] = values[i];
//        pindx[index] = i;
//        index++;
//      }
//    }
//    return new int[][] { pvals, pindx };
//  }
//
//  /** Sort two arrays - the second one is sorted according the first one. */
//  public static void sortWith(final int[] ary, int[] ary2) {
//    Integer[] sortOrder = new Integer[ary.length];
//    for(int i=0; i<sortOrder.length; i++) sortOrder[i] = i;
//    Arrays.sort(sortOrder, new Comparator<Integer>() {
//      @Override public int compare(Integer o1, Integer o2) { return ary[o1]-ary[o2]; }
//    });
//    sortAccording2(ary,  sortOrder);
//    sortAccording2(ary2, sortOrder);
//  }
//
//  /** Sort given array according given sort order. Sort is implemented in-place. */
//  public static void sortAccording2(int[] ary, Integer[] sortOrder) {
//    Integer[] so = sortOrder.clone(); // we are modifying sortOrder to preserve exchanges
//    for(int i=0; i<ary.length; i++) {
//      int tmp = ary[i];
//      int idx = so[i];
//      ary[i] = ary[idx];
//      ary[idx] = tmp;
//      for (int j=i; j<so.length; j++) if (so[j]==i) { so[j] = idx; break; }
//    }
//  }
//  /** Sort given array according given sort order. Sort is implemented in-place. */
//  private static void sortAccording2(boolean[] ary, Integer[] sortOrder) {
//    Integer[] so = sortOrder.clone(); // we are modifying sortOrder to preserve exchanges
//    for(int i=0; i<ary.length; i++) {
//      boolean tmp = ary[i];
//      int idx = so[i];
//      ary[i] = ary[idx];
//      ary[idx] = tmp;
//      for (int j=i; j<so.length; j++) if (so[j]==i) { so[j] = idx; break; }
//    }
//  }
}
