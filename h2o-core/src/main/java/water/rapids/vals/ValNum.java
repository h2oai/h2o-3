package water.rapids.vals;

import water.rapids.Val;

/**
 * Numeric value. We do not distinguish between integers and floating point numbers.
 */
public class ValNum extends Val {
  private double _d;

  public ValNum(double d) {
    _d = d;
  }

  @Override public int type() { return NUM; }
  @Override public boolean isNum() { return true; }
  @Override public double getNum() { return _d; }
  @Override public String toString() { return String.valueOf(_d); }

  public void setNum(double d) {
    _d = d;
  }
}
