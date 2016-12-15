package ai.h2o.cascade.asts;

import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * A collection of simple numbers (doubles), usable in contexts where an array
 * of real (or integer) numbers is required: {@code AstHist}, {@code AstQtile},
 * {@code AstScale}, etc.
 * <p>
 * When executed, this AST produces a {link ValNums} wrapper around the
 * underlying array of numbers.
 *
 * see AstSliceList
 */
public class AstNumList extends Ast<AstNumList> {
  private double[] items;


  public AstNumList(ArrayList<Double> nums) {
    items = ArrayUtils.toDoubleArray(nums);
  }

  /**
   * Return the numlist as a plain {@code double[]} array.
   */
  public double[] items() {
    return items;
  }

  /**
   * Sort the numbers in-place and return them as an array. NaNs will be
   * sorted as being greater than all other entries.
   */
  public double[] sorted() {
    Arrays.sort(items);
    return items;
  }

  @Override
  public String str() {
    return Arrays.toString(items);
  }

}
