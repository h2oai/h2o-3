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
  public boolean maybeNums() {
    return true;
  }

  @Override
  public double[] getNums() {
    return nums;
  }
}
