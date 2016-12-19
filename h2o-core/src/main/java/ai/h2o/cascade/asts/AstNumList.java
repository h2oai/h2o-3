package ai.h2o.cascade.asts;

import ai.h2o.cascade.CascadeScope;
import ai.h2o.cascade.vals.Val;
import ai.h2o.cascade.vals.ValNums;
import water.util.ArrayUtils;

import java.util.ArrayList;
import java.util.Arrays;


/**
 * A collection of simple numbers (doubles), usable in contexts where an array
 * of real numbers is required: {@code AstHist}, {@code AstQtile},
 * {@code AstScale}, etc.
 * <p>
 * When executed, this AST produces a {link ValNums} wrapper around the
 * underlying array of numbers.
 */
public class AstNumList extends Ast<AstNumList> {
  private double[] items;


  public AstNumList(ArrayList<Double> nums) {
    items = ArrayUtils.toDoubleArray(nums);
  }

  @Override
  public Val exec(CascadeScope scope) {
    return new ValNums(items);
  }

  @Override
  public String str() {
    return Arrays.toString(items);
  }

}
