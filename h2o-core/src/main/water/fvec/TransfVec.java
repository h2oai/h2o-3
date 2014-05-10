package water.fvec;

import java.util.Arrays;
import java.util.Comparator;
import water.*;
import water.util.ArrayUtils;

/**
 * Dummy vector transforming values of given vector according to given domain mapping.
 *
 * <p>The mapping is defined by a simple hash map composed of two arrays.
 * The first array contains values. Index of values is index into the second array {@link #_indexes}
 * which contains final value (i.e., index to domain array).</p>
 *
 * <p>If {@link #_indexes} array is null, then index of found value is used directly.</p>
 *
 * <p>To avoid virtual calls or additional null check for {@link #_indexes} the vector
 * returns two implementation of underlying chunk ({@link TransfChunk} when {@link #_indexes} is not <code>null</code>,
 * and {@link FlatTransfChunk} when {@link #_indexes} is <code>null</code>.</p>
 */
public class TransfVec extends WrappedVec {
  /** List of values from underlying vector which this vector map to a new value. If
   * a value is not included in this array the implementation returns NA. */
  final int[] _values;
  /** The transformed value - i.e. transformed value is: <code>int idx = find(value, _values); return _indexes[idx]; </code> */
  final int[] _indexes;

  public TransfVec(int[][] mapping, Key masterVecKey, Key key, long[] espc) {
    this(mapping, null, masterVecKey, key, espc);
  }
  public TransfVec(int[][] mapping, String[] domain, Key masterVecKey, Key key, long[] espc) {
    this(mapping[0], mapping[1], domain, masterVecKey, key, espc);
  }
  public TransfVec(int[] values, int[] indexes, String[] domain, Key masterVecKey, Key key, long[] espc) {
    super(masterVecKey, key, espc);
    _values  =  values;
    _indexes =  indexes;
    _domain = domain;
  }

  @Override public Chunk chunkForChunkIdx(int cidx) {
    Chunk c = masterVec().chunkForChunkIdx(cidx);
    if (_indexes!=null) // two way mapping
      return new TransfChunk(c, this);
    else // single way mapping
      return new FlatTransfChunk(c, this);
  }

  static abstract class AbstractTransfChunk extends Chunk {
    protected static final long MISSING_VALUE = -1L;
    final Chunk _c;

    protected AbstractTransfChunk(Chunk c, TransfVec vec) { _c  = c; _len = _c._len; _start = _c._start; _vec = vec; }

    @Override protected double atd_impl(int idx) { double d = 0; return _c.isNA0(idx) ? Double.NaN : ( (d=at8_impl(idx)) == MISSING_VALUE ? Double.NaN : d ) ;  }
    @Override protected boolean isNA_impl(int idx) {
      if (_c.isNA_impl(idx)) return true;
      return at8_impl(idx) == MISSING_VALUE; // this case covers situation when there is no mapping
    }

    @Override boolean set_impl(int idx, long l)   { return false; }
    @Override boolean set_impl(int idx, double d) { return false; }
    @Override boolean set_impl(int idx, float f)  { return false; }
    @Override boolean setNA_impl(int idx)         { return false; }
    @Override NewChunk inflate_impl(NewChunk nc) {
      nc._xs = MemoryManager.malloc4(_len);
      nc._ls = MemoryManager.malloc8(_len);
      for( int i=0; i<_len; i++ ) {
        if(isNA0(i)) nc.setNA_impl2(i);
        else nc._ls[i] = at80(i);
      }
      return nc;
    }
    @Override public AutoBuffer write_impl(AutoBuffer bb) { throw new UnsupportedOperationException(); }
    @Override public Chunk read_impl(AutoBuffer bb)       { throw new UnsupportedOperationException(); }

  }

  static class TransfChunk extends AbstractTransfChunk {
    /** @see TransfVec#_values */
    final int[] _values;
    /** @see TransfVec#_indexes */
    final int[] _indexes;
    public TransfChunk(Chunk c, TransfVec vec) {
      super(c,vec);
      assert vec._indexes != null : "TransfChunk needs not-null indexing array.";
      _values = vec._values;
      _indexes = vec._indexes;
    }

    @Override protected long at8_impl(int idx) { return get(_c.at8_impl(idx)); }

    private long get(long val) {
      int indx = -1;
      return (indx = Arrays.binarySearch(_values, (int)val)) < 0 ? MISSING_VALUE : _indexes[indx];
    }
  }

  static class FlatTransfChunk extends AbstractTransfChunk {
    /** @see TransfVec#_values */
    final int[] _values;

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

  /** Compose this vector with given transformation. Always return a new vector */
  public Vec compose(int[][] transfMap, String[] domain) { return compose(this, transfMap, domain, true);  }

  /**
   * Compose given origVector with given transformation. Always returns a new vector.
   * Original vector is kept if keepOrig is true.
   * @param origVec
   * @param transfMap
   * @param keepOrig
   * @return a new instance of {@link TransfVec} composing transformation of origVector and tranfsMap
   */
  public static Vec compose(TransfVec origVec, int[][] transfMap, String[] domain, boolean keepOrig) {
    throw H2O.unimpl();
    //// Do a mapping from INT -> ENUM -> this vector ENUM
    //int[][] domMap = Utils.compose(new int[][] {origVec._values, origVec._indexes }, transfMap);
    //Vec result = origVec.masterVec().makeTransf(domMap[0], domMap[1], domain);;
    //if (!keepOrig) DKV.remove(origVec._key);
    //return result;
  }

  static int[][] compose(int[][] first, int[][] second) {
    int[] firstDom = first[0];
    int[] firstRan = first[1];  // flat transformation
    int[] secondDom = second[0];
    int[] secondRan = second[1];

    boolean[] filter = new boolean[firstDom.length]; int fcnt = 0;
    int[] resDom = firstDom.clone();
    int[] resRan = firstRan!=null ? firstRan.clone() : new int[firstDom.length];
    for (int i=0; i<resDom.length; i++) {
      int v = firstRan!=null ? firstRan[i] : i; // resulting value
      int vi = Arrays.binarySearch(secondDom, v);
      // Do not be too strict in composition assert vi >=0 : "Trying to compose two incompatible transformation: first=" + Arrays.deepToString(first) + ", second=" + Arrays.deepToString(second);
      if (vi<0) {
        filter[i] = true;
        fcnt++;
      } else
        resRan[i] = secondRan!=null ? secondRan[vi] : vi;
    }
    return new int[][] { filter(resDom,filter,fcnt), filter(resRan,filter,fcnt) };
  }
  private static int[] filter(int[] values, boolean[] filter, int fcnt) {
    assert filter.length == values.length : "Values should have same length as filter!";
    assert filter.length - fcnt >= 0 : "Cannot filter more values then legth of filter vector!";
    if (fcnt==0) return values;
    int[] result = new int[filter.length - fcnt];
    int c = 0;
    for (int i=0; i<values.length; i++) {
      if (!filter[i]) result[c++] = values[i];
    }
    return result;
  }

  public static int[][] pack(int[] values, boolean[] usemap) {
    assert values.length == usemap.length : "Cannot pack the map according given use map!";
    int cnt = 0;
    for (boolean anUsemap : usemap) cnt += anUsemap ? 1 : 0;
    int[] pvals = new int[cnt]; // only used values
    int[] pindx = new int[cnt]; // indexes of used values
    int index = 0;
    for (int i=0; i<usemap.length; i++) {
      if (usemap[i]) {
        pvals[index] = values[i];
        pindx[index] = i;
        index++;
      }
    }
    return new int[][] { pvals, pindx };
  }

  /** Sort two arrays - the second one is sorted according the first one. */
  public static void sortWith(final int[] ary, int[] ary2) {
    Integer[] sortOrder = new Integer[ary.length];
    for(int i=0; i<sortOrder.length; i++) sortOrder[i] = i;
    Arrays.sort(sortOrder, new Comparator<Integer>() {
      @Override public int compare(Integer o1, Integer o2) { return ary[o1]-ary[o2]; }
    });
    sortAccording2(ary,  sortOrder);
    sortAccording2(ary2, sortOrder);
  }

  /** Sort given array according given sort order. Sort is implemented in-place. */
  public static void sortAccording2(int[] ary, Integer[] sortOrder) {
    Integer[] so = sortOrder.clone(); // we are modifying sortOrder to preserve exchanges
    for(int i=0; i<ary.length; i++) {
      int tmp = ary[i];
      int idx = so[i];
      ary[i] = ary[idx];
      ary[idx] = tmp;
      for (int j=i; j<so.length; j++) if (so[j]==i) { so[j] = idx; break; }
    }
  }
  /** Sort given array according given sort order. Sort is implemented in-place. */
  private static void sortAccording2(boolean[] ary, Integer[] sortOrder) {
    Integer[] so = sortOrder.clone(); // we are modifying sortOrder to preserve exchanges
    for(int i=0; i<ary.length; i++) {
      boolean tmp = ary[i];
      int idx = so[i];
      ary[i] = ary[idx];
      ary[idx] = tmp;
      for (int j=i; j<so.length; j++) if (so[j]==i) { so[j] = idx; break; }
    }
  }
}
