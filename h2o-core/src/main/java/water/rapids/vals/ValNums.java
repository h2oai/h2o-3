package water.rapids.vals;

import water.rapids.Val;

import java.util.Arrays;

/**
 * Array of numbers
 */
public class ValNums extends Val {
  private final double[] _ds;

  public ValNums(double[] ds) {
    _ds = ds;
  }

  @Override public int type() { return NUMS; }
  @Override public boolean isNums() { return true; }
  @Override public double[] getNums() { return _ds; }
  @Override public String toString() { return Arrays.toString(_ds); }
}
