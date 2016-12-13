package water.rapids.vals;

import water.rapids.Val;

/**
 * Null value.
 *
 * <p>This value may be either produced by a Rapids literal {@code null}, or
 * returned by some builtin function that normally would return {@code void}.
 *
 * <p>The null value can be treated as NaN when used in a place where a
 * numeric value is expected, or as a null string when used in a string
 * context. Later we may add more conversions into other types, if it would
 * seem reasonable to do.
 */
public class ValNull extends Val {

  @Override public int type() {
    return Val.NULL;
  }

  @Override public boolean isNum() {
    return true;
  }

  @Override public boolean isStr() {
    return true;
  }

  @Override public double getNum() {
    return Double.NaN;
  }

  @Override public String getStr() {
    return null;
  }

  @Override public String toString() {
    return "null";
  }

}
