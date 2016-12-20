package ai.h2o.cascade.vals;

import ai.h2o.cascade.core.SliceList;


/**
 * Numeric (double) value.
 */
public class ValNum extends Val {
  private double value;


  public ValNum(double d) {
    value = d;
  }

  public ValNum(long d) {
    value = d;
  }

  public ValNum(int d) {
    value = d;
  }

  public ValNum(boolean d) {
    value = d? 1 : 0;
  }

  @Override public Type type() {
    return Type.NUM;
  }

  @Override public String toString() {
    return String.valueOf(value);
  }


  //--------------------------------------------------------------------------------------------------------------------
  // Value representations
  //--------------------------------------------------------------------------------------------------------------------

  @Override public boolean maybeNum() {
    return true;
  }

  @Override public double getNum() {
    return value;
  }


  @Override public boolean maybeInt() {
    return (int) value == value;
  }

  @Override public int getInt() {
    return (int) value;
  }


  @Override public boolean maybeBool() {
    return value == 0 || value == 1;
  }

  @Override public boolean getBool() {
    return value != 0;
  }

  @Override public boolean maybeSlice() {
    return (long) value == value;
  }

  @Override public SliceList getSlice() {
    return new SliceList((long) value);
  }
}
