package ai.h2o.cascade.core;

import water.Iced;
import water.util.ArrayUtils;

import java.util.ArrayList;

/**
 */
public class SliceList extends Iced {
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


  public SliceList(long[] numbers) {
    bases = numbers;
    isList = true;
    isSorted = ArrayUtils.isSorted(bases);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Public methods
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


}
