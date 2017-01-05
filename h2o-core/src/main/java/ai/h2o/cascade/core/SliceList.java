package ai.h2o.cascade.core;

import water.util.ArrayUtils;
import water.util.SB;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * TODO: clean up
 *
 * A collection of base/stride/cnts.
 * Syntax: [ {num | num:cnt | num:cnt:stride},* ]
 * <p/>
 * The bases can be unordered with dups (often used for column selection where
 * repeated columns are allowed, and order matters).  The isList flag tracks
 * that all cnts are 1 (and hence all strides are ignored and 1); these lists
 * may or may not be sorted.  Note that some column selection is dense
 * (typical all-columns is: {0:MAX_INT}), and this has cnt>1.
 * <p/>
 * When cnts are > 1, bases must be sorted, with base+stride*cnt always less
 * than the next base.  Typical use-case might be a list of probabilities for
 * computing quantiles, or grid-search parameters.
 * <p/>
 * Asking for a sorted integer expansion will sort the bases internally, and
 * also demand no overlap between bases.  The has(), min() and max() calls
 * require a sorted list.
 */
public class SliceList extends Val {
  public static final int LARGEST_LIST_TO_EXPAND = 1000000;

  private long[] bases;
  private long[] counts;
  private long[] strides;

  /**
   * True if this is a "plain" list of numbers (all counts and strides are 1).
   * When this flag is true, arrays {@code counts} and {@code strides} may be
   * {@code null}.
   */
  private boolean isList;

  /** True if all items in the list are sorted (in ascending order). */
  private boolean isSorted;


  //--------------------------------------------------------------------------------------------------------------------
  // Constructors
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Standard constructor, building the {@code SliceList} out of
   * {@code ArrayList}s of {@code bases}, {@code counts} and {@code strides}.
   */
  public SliceList(ArrayList<Long> basesList, ArrayList<Long> countsList, ArrayList<Long> stridesList) {
    bases = ArrayUtils.toLongArray(basesList);
    counts = ArrayUtils.toLongArray(countsList);
    strides = ArrayUtils.toLongArray(stridesList);

    // Detect whether the list is a plain list and/or is sorted
    isList = true;
    isSorted = true;
    for (int i = 0; i < bases.length; i++) {
      if (counts[i] != 1) isList = false;
      if (strides[i] < 0 || i > 0 && bases[i-1] + (counts[i-1] - 1) * strides[i-1] > bases[i]) isSorted = false;
    }
  }


  /** Make a simple list of 1 number. */
  public SliceList(long n) {
    bases = new long[]{n};
    isList = true;
    isSorted = true;
  }


  /**
   * Make a list of numbers {@code [start, start + 1, ..., end - 1]}, this
   * is equivalent to Python's {@code range(start, end)}. For this method
   * {@code start} should be less than {@code end}.
   */
  public SliceList(long start, long end) {
    assert start < end;
    bases = new long[]{start};
    counts = new long[]{end - start};
    strides = new long[]{1};
    isList = false;
    isSorted = true;
  }


  /**
   * Make a SliceList out of a plain list of numbers;
   */
  public SliceList(long[] numbers) {
    bases = numbers;
    isList = true;
    isSorted = ArrayUtils.isSorted(bases);
  }


  /**
   * Make a SliceList from the list of column names in a frame.
   */
  public SliceList(String[] names, GhostFrame frame) {
    bases = new long[names.length];
    for (int i = 0; i < names.length; i++) {
      int j = frame.findColumnByName(names[i]);
      if (j == -1)
        throw new IllegalArgumentException("Column '" + names[i] + "' was not found in the frame");
      bases[i] = j;
    }
    isList = true;
    isSorted = ArrayUtils.isSorted(bases);
  }


  /** Internal; public only for Externalizable purposes */
  public SliceList() {}


  //--------------------------------------------------------------------------------------------------------------------
  // List properties
  //--------------------------------------------------------------------------------------------------------------------

  /** Return true if the indices are sorted in ascending order. */
  public boolean isSorted() {
    return isSorted;
  }


  /**
   * Return true if the list of indices represents a sequence (a, a+1, ... b)
   * for some a and b. Thus, a dense list is the one without any gaps.
   */
  public boolean isDense() {
    if (isList) {
      for (int i = 1; i < bases.length; ++i) {
        if (bases[i] != bases[i-1] + 1)
          return false;
      }
    } else {
      for (int i = 0; i < bases.length; i++) {
        if (strides[i] != 1) return false;
        if (i > 0 && bases[i] != bases[i-1] + counts[i-1]) return false;
      }
    }
    return true;
  }


  /**
   * Return the total number of indices represented by this multi-index list.
   * For example, {@code size(<3:2:2 5:1000>)} is 1002.
   */
  public long size() {
    return isList? bases.length : ArrayUtils.sum(counts);
  }


  /**
   * Return the first index in the list.
   */
  public long first() {
    return bases[0];
  }


  /**
   * Return an iterator over this sequence of indices.
   */
  public Iterator iter() {
    return isList? new SimpleSliceIterator() : new MultiSliceIterator();
  }


  /**
   * Helper interface for iteration over the slice list.
   * <p>
   * In addition to standard {@code Iterator<Long>}, this interface also
   * implements {@link #nextPrim()} which works like {@link #next()}
   * except that it returns a primitive {@code long} value instead of boxed
   * {@code Long}. Thus, using {@link #nextPrim()} may improve performance.
   */
  public interface Iterator extends java.util.Iterator<Long> {
    long nextPrim();
    void reset();
  }


  //--------------------------------------------------------------------------------------------------------------------
  // List manipulation
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Expand the compressed form into an array of ints; may be used for lists
   * that are expected to be reasonably small, such as column indices.
   *
   * @throws IllegalArgumentException if number of elements in the expanded
   *     list would exceed {@link #LARGEST_LIST_TO_EXPAND}.
   */
  public int[] expand4() {
    long nelems = size();
    if (nelems > LARGEST_LIST_TO_EXPAND)
      throw new IllegalArgumentException("Refuse to expand list with more than " + nelems + " elements.");

    int[] res = new int[(int) nelems];
    Iterator iterator = iter();
    int i = 0;
    while (iterator.hasNext()) {
      res[i++] = (int) iterator.nextPrim();
    }
    assert i == nelems;
    return res;
  }


  /**
   * Expand the compressed form into an array of ints; may be used for lists
   * that are expected to be reasonably small, such as column indices.
   *
   * @throws IllegalArgumentException if number of elements in the expanded
   *     list would exceed {@link #LARGEST_LIST_TO_EXPAND}.
   */
  public long[] expand8() {
    long nelems = size();
    if (nelems > LARGEST_LIST_TO_EXPAND)
      throw new IllegalArgumentException("Refuse to expand list with more than " + nelems + " elements.");

    long[] res = new long[(int) nelems];
    Iterator iterator = iter();
    int i = 0;
    while (iterator.hasNext()) {
      res[i++] = iterator.nextPrim();
    }
    assert i == nelems;
    return res;
  }


  /**
   * Check and normalize the slice list, assuming it will be applied to a list
   * with {@code n} elements. Actual normalization consists of treating
   * the negative elements as "take everything except those" and translating
   * this into slices. Note that these negative indices are 1-based
   * (i.e. -1 means take all rows except the first), and cannot be combined
   * with the positive indices. For example, {@code <-1, -3>} is valid and
   * means take all elements except the first and the third, whereas
   * {@code <-1, 1>} is invalid.
   *
   * @param n number of items in the array to which the index will be applied.
   *          This is needed for AIOOBE checking, and in order to expand the
   *          negative indices.
   */
  public SliceList normalizeR(long n) {
    boolean negate = false;
    for (int i = 0; i < bases.length; i++) {
      long rfrst = bases[i];
      long rlast = isList? rfrst : rfrst + strides[i] * (counts[i] - 1);
      if (rfrst < 0 || rlast < 0) {
        negate = true;
      }
      if (negate) {
        if (rfrst < -n || rlast < -n || rfrst >= 0 || rlast >= 0)
          throw new IllegalArgumentException("Elements in the index are not within the [-" + n + " .. -1] range");
      } else {
        if (rfrst < 0 || rlast <0 || rfrst >= n || rlast >= n)
          throw new IllegalArgumentException("Elements in the index are not within the [0.." + n + ") range");
      }
    }
    if (negate) {
      // Example: suppose n = 10 and the original list is
      //     <-7, -6, -5, -3, -5, -1>
      // First step sorts and negates, making it the list of indices to exclude:
      //     <0, 2, 4, 4, 5, 6>
      // Second step counts the number of "gaps" in this list (3), which will
      // be the number of triples in the final multi-index.
      // Last step creates new bases/counts/strides arrays, consisting of the
      // gaps in the original array:
      //     <1:1, 3:1, 7:3>
      //
      long[] bases0 = isList? bases : expand8();
      int nbases = bases0.length;

      int[] bases1 = new int[nbases];
      for (int i = 0; i < nbases; i++)
        bases1[i] = (int) -bases0[i] - 1;
      Arrays.sort(bases1);
      assert bases1[0] >= 0 && bases1[nbases - 1] < n : "Unexpected bases list: " + Arrays.toString(bases1);

      int ngaps = (bases1[0] == 0? 0 : 1) + (bases1[nbases - 1] == n - 1? 0 : 1);
      for (int i = 1; i < nbases; i++)
        if (bases1[i] - bases1[i - 1] >= 2)
          ngaps++;

      long[] bases2 = new long[ngaps];
      long[] counts2 = new long[ngaps];
      long[] strides2 = new long[ngaps];
      int igap = 0;
      if (bases1[0] > 0) {
        bases2[igap] = 0;
        counts2[igap] = bases1[0];
        strides2[igap++] = 1;
      }
      for (int i = 1; i < nbases; i++)
        if (bases1[i] - bases1[i-1] >= 2) {
          bases2[igap] = bases1[i-1] + 1;
          counts2[igap] = bases1[i] - bases1[i-1] - 1;
          strides2[igap++] = 1;
        }
      if (bases1[nbases-1] < n - 1) {
        bases2[igap] = bases1[nbases-1] + 1;
        counts2[igap] = n - bases1[nbases-1];
        strides2[igap++] = 1;
      }
      assert igap == ngaps;

      SliceList sl = new SliceList();
      sl.bases = bases2;
      sl.counts = counts2;
      sl.strides = strides2;
      sl.isList = false;
      sl.isSorted = true;
      return sl;
    } else {
      return this;
    }
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Val interface implementation
  //--------------------------------------------------------------------------------------------------------------------

  @Override
  public Val.Type type() {
    return Val.Type.SLICE;
  }

  @Override
  public boolean isSlice() {
    return true;
  }

  @Override
  public SliceList getSlice() {
    return this;
  }


  @Override
  public String toString() {
    SB sb = new SB().p('<');
    for (int i = 0; i < bases.length; i++) {
      sb.p(bases[i]);
      if (counts[i] != 1) {
        sb.p(':').p(counts[i]);
      }
      if (strides[i] != 1) {
        sb.p(':').p(strides[i]);
      }
      if (i < bases.length - 1)
        sb.p(' ');
    }
    return sb.p('>').toString();
  }



  //--------------------------------------------------------------------------------------------------------------------
  // Private
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Iterator over a plain list of indices (when {@code isList} is true).
   */
  public class SimpleSliceIterator implements Iterator {
    private int i;
    private int n;

    public SimpleSliceIterator() {
      assert isList;
      i = 0;
      n = bases.length;
    }

    @Override public boolean hasNext() { return i < n; }
    @Override public long nextPrim() { return bases[i++]; }
    @Override public Long next() { return bases[i++]; }
    @Override public void remove() {}
    @Override public void reset() { i = 0; }
  }


  /**
   * Iterator over a generic SliceList.
   */
  private class MultiSliceIterator implements Iterator {
    private int i;  // Index of the current triple base:count:stride
    private int n;  // Total number of such triples - 1
    private long prevValue;  // Previous yielded value
    private long step;       // Current step of the iteration, can be positive, negative, or 0
    private long stopValue;  // Last value to yield before iteration within current triple should stop
                             // Note: if step=0 we set stopValue = base + count, and then decrement this
                             // stopValue on every iteration

    public MultiSliceIterator() {
      i = -1;
      n = bases.length - 1;
      nextStep();
    }

    @Override
    public boolean hasNext() {
      return prevValue != stopValue || i < n;
    }

    public long nextPrim() {
      if (prevValue == stopValue) nextStep();
      if (step == 0) stopValue--;
      return (prevValue += step);
    }

    @Override
    public Long next() {
      return nextPrim();
    }

    @Override
    public void reset() {
      i = -1;
      nextStep();
    }

    @Override public void remove() {}

    private void nextStep() {
      i++;
      step = strides[i];
      prevValue = bases[i] - step;
      stopValue = prevValue + counts[i] * step;
      if (step == 0) stopValue += counts[i];
    }

  }





  /*
   *---------------------------------------------------------------------
   * (Moved from AsSliceList)
   *---------------------------------------------------------------------

  /**
   * Check if {@code n} is in this multi-index.
   * Only works for sorted indices.
   * /
  public boolean has(long n) {
    assert isSorted;
    // Arrays.binarySearch returns either the index of element found, or (-(insertion point) - 1).
    int idx = Arrays.binarySearch(bases, n);
    if (idx >= 0) return true;
    // Insertion point minus 1: (index of the first element greater than n) - 1.
    int j = -idx - 1 - 1;
    return j >= 0 && n <= bases[j] + (counts[j] - 1) * strides[j] && (n - bases[j]) % strides[j] == 0;
  }


  public boolean isEmpty() {
    return bases.length == 0;
  }

  // Update-in-place sort of bases
  public AstSliceList sort() {
    if (isSorted) return this;  // Flow coding fast-path cutout
    int[] idxs = ArrayUtils.seq(0, bases.length);
    ArrayUtils.sort(idxs, bases);
    double[] bases = this.bases.clone();
    double[] strides = this.strides.clone();
    long[] cnts = counts.clone();
    for (int i = 0; i < idxs.length; i++) {
      this.bases[i] = bases[idxs[i]];
      this.strides[i] = strides[idxs[i]];
      counts[i] = cnts[idxs[i]];
    }
    isSorted = true;
    return this;
  }


  /**
   * Finds index of a given value in this number sequence, indexing start at 0.
   * @param v value
   * @return value index (>= 0) or -1 if value is not a member of this sequence
   * /
  public long index(long v) {
    assert isSorted;
    int bIdx = Arrays.binarySearch(bases, v);
    if (bIdx >= 0) return ArrayUtils.sum(counts, 0, bIdx - 1);
    bIdx = -bIdx - 2;
    if (bIdx < 0) return -1L;
    assert bases[bIdx] < v;
    long offset = v - bases[bIdx];
    long stride = strides[bIdx];
    if ((offset >= counts[bIdx] * stride) || (offset % stride != 0)) return -1L;
    return ArrayUtils.sum(counts, 0, bIdx) + (offset / stride);
  }



  // Select columns by number.  Numbers are capped to the number of columns +1
  // - this allows R      to see a single out-of-range value and throw a range check
  // - this allows Python to see a single out-of-range value and ignore it
  // - this allows Python to pass [0:MAXINT] without blowing out the max number of columns.
  // Note that the Python front-end does not want to cap the max column size, because
  // this will force eager evaluation on a standard column slice operation.
  // Note that the list is often unsorted (isSorted is false).
  // Note that the list is often dense with cnts>1 (isList is false).
  public int[] columns(String[] names) {
    long n = names.length - 1;  // largest allowed index
    int m = bases.length;      // number of triples base:count:stride
    // Count total values, capped by n; also verify arguments correctness
    int nrows = 0;
    for (int i = 0; i < m; i++) {
      int base = (int) bases[i];
      if (base != bases[i])
        throw new IllegalArgumentException("Indices must be integer in column-selection num lists");
      if (base > n)
        throw new IllegalArgumentException("Index is out of bounds: " + base + " (for " + (n+1) + " columns frame)");
      long count = counts[i];
      int stride = (int) strides[i];
      if (stride != strides[i])
        throw new IllegalArgumentException("Strides must be integer in column-selection num lists");
      // Triple base:count:stride represents a sequence
      //     base, base + stride, base + 2*stride, ..., base + (count-1)*stride
      // The number of elements (i) below the cap n satisfies
      //     base + (i - 1)*stride <= n  ==>  i <= (n - base)/stride + 1  ==>  i = floor((n - base)/stride) + 1
      // Hence use integer division below:
      nrows += Math.min(count, (n - base) / stride + 1);
    }
    // Fill in values
    int[] vals = new int[nrows];
    int r = 0;
    for (int i = 0; i < m; i++) {
      int base = (int) bases[i];
      int stride = (int) strides[i];
      int max = (int) Math.min(base + (counts[i] - 1)*stride, n);
      for (int d = base; d <= max; d += stride)
        vals[r++] = d;
    }
    return vals;
  }

*/

}
