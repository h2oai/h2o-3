package ai.h2o.cascade.asts;

import ai.h2o.cascade.core.Scope;
import ai.h2o.cascade.core.SliceList;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValSlice;
import water.util.ArrayUtils;
import water.util.SB;

import java.util.ArrayList;
import java.util.Arrays;

/**
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
public class AstSliceList extends Ast<AstSliceList> {
  private SliceList sliceList;


  public AstSliceList(SliceList sl) {
    sliceList = sl;
  }

  public AstSliceList(ArrayList<Long> bases, ArrayList<Long> counts, ArrayList<Long> strides) {
    sliceList = new SliceList(bases, counts, strides);
  }

  @Override
  public Val exec(Scope scope) {
    return new ValSlice(sliceList);
  }



  //
  //
  // TODO: move all of this functionality below into the {@code SliceList} class.
  //
  //


  public static final int LARGEST_LIST_TO_EXPAND = 1000000;

  private long[] bases;
  private long[] counts;
  private long[] strides;

  /**
   * True if this is a "plain" list of numbers (all counts and strides are 1).
   * Note that when this flag is true, arrays {@code counts} and
   * {@code strides} may be {@code null}.
   */
  private boolean isList;

  /**
   * True if all items in the list are sorted (in ascending order).
   */
  private boolean isSorted;




  //--------------------------------------------------------------------------------------------------------------------
  // Ast implementation
  //--------------------------------------------------------------------------------------------------------------------



  @Override
  public String str() {
    SB sb = new SB().p('[');
    for (int i = 0; i < bases.length; i++) {
      sb.p(bases[i]);
      if (counts[i] != 1) {
        sb.p(':').p(counts[i]);
      }
      if (strides[i] != 1) {
        sb.p(':').p(strides[i]);
      }
      if (i < bases.length - 1)
        sb.p(", ");
    }
    return sb.p(']').toString();
  }

  //--------------------------------------------------------------------------------------------------------------------
  // Public methods
  //--------------------------------------------------------------------------------------------------------------------

  /**
   * Expand the compressed form into an array of ints; may be used for lists
   * that are expected to be reasonably small, such as column indices.
   *
   * @throws IllegalArgumentException if number of elements in the expanded
   *     list would exceed {@link #LARGEST_LIST_TO_EXPAND}.
   */
  public int[] expand4() {
    long nrows = isList? bases.length : ArrayUtils.sum(counts);
    if (nrows > LARGEST_LIST_TO_EXPAND)
      throw new IllegalArgumentException("Refuse to expand list with more than " + nrows + " elements.");
    // At this point conversion to int is already safe
    int[] res = new int[(int) nrows];

    if (isList) {
      for (int i = 0; i < res.length; i++)
        res[i] = (int) bases[i];
      return res;
    }
    // Fill in values
    for (int i = 0, r = 0; i < bases.length; i++) {
      long step = strides[i];
      long maxd = bases[i] + counts[i] * step;
      for (long d = bases[i]; d < maxd; d += step)
        res[r++] = (int) d;
    }
    return res;
  }


  /**
   * Smallest value in this multi-index.
   */
  public long min() {
    assert isSorted : "Not supported for unsorted lists";
    return bases[0];
  }

  /**
   * Largest (inclusive) value in this multi-index.
   */
  public long max() {
    assert isSorted : "Not supported for unsorted lists";
    int n = bases.length - 1;
    return bases[n] + strides[n] * (counts[n] - 1);
  }

  /**
   * Check if {@code n} is in this multi-index.
   * Only works for sorted indices.
   */
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


/*

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

  // Expand the compressed form into an array of longs;
  // often used for unordered row lists
  public long[] expand8() {
    // Count total values
    int nrows = (int) cnt(), r = 0;
    // Fill in values
    long[] vals = new long[nrows];
    for (int i = 0; i < bases.length; i++)
      for (double d = bases[i]; d < bases[i] + counts[i] * strides[i]; d += strides[i])
        vals[r++] = (long) d;
    return vals;
  }

  // Expand the compressed form into an array of longs;
  // often used for sorted row lists
  public long[] expand8Sort() {
    return sort().expand8();
  }



  public boolean isDense() {
    return counts.length == 1 && bases[0] == 0 && strides[0] == 1;
  }


*/

  /**
   * Finds index of a given value in this number sequence, indexing start at 0.
   * @param v value
   * @return value index (>= 0) or -1 if value is not a member of this sequence
   */
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
}
