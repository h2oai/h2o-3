package ai.h2o.cascade.vals;


/**
 * Null value.
 *
 * <p>This value may be either produced by a literal {@code null}, or
 * returned by some builtin function that normally would return {@code void}.
 *
 * <p>The null value can be treated as NaN when used in a place where a
 * numeric value is expected, or as a null string when used in a string
 * context. Later we may add more conversions into other types, if it would
 * seem reasonable to do.
 */
public class ValNull extends Val {

  @Override public Type type() {
    return Type.NULL;
  }

  @Override public String toString() {
    return "null";
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Value representations
  //--------------------------------------------------------------------------------------------------------------------

  @Override public boolean maybeNum() {
    return true;
  }

  @Override public double getNum() {
    return Double.NaN;
  }


  @Override public boolean maybeStr() {
    return true;
  }

  @Override public String getStr() {
    return null;
  }

}
