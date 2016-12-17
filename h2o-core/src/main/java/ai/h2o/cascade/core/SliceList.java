package ai.h2o.cascade.core;

/**
 */
public class SliceList {
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
  // Constructors
  //--------------------------------------------------------------------------------------------------------------------

  /** Make a simple list of 1 number. */
  public SliceList(long n) {
    bases = new long[]{n};
    isList = true;
    isSorted = true;
  }

}
