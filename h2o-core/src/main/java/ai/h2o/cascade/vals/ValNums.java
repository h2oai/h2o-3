package ai.h2o.cascade.vals;

/**
 * Value corresponding to an array of numbers.
 */
public class ValNums extends Val {
  private double[] nums;

  public ValNums(double[] arr) {
    nums = arr;
  }

  @Override
  public Type type() {
    return Type.NUMS;
  }

  @Override
  public boolean isNums() {
    return true;
  }

  @Override
  public double[] getNums() {
    return nums;
  }


  // When {@code nums} array is empty, it can be treated as an empty StrList as well
  @Override
  public boolean isStrs() {
    return nums.length == 0;
  }

  @Override
  public String[] getStrs() {
    if (nums.length == 0) return new String[0];
    return super.getStrs();
  }
}
