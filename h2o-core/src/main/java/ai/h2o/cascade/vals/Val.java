package ai.h2o.cascade.vals;

import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.core.IdList;
import ai.h2o.cascade.core.SliceList;
import water.fvec.Frame;

/**
 * Base class for all Value objects passed around the Cascade system.
 *
 * <p> Each value has an associated {@link #type()}, which is in 1-to-1
 * correspondence with the value's class.
 *
 * <p> Most values are simple wrappers around simple Java types, such as
 * {@code double}, {@code null}, {@code String}, {@code double[]}, etc. Other
 * values wrap around core H2O or Cascade objects: {@code Frame},
 * {@code WorkFrame}, {@code SliceList}, {@code Function}, etc.
 *
 * <p> In many circumstances values of different types may be interchangeable.
 * For example, a {@code NULL} value may be used in place of almost all other
 * values. A {@code NUMS} list may be interchangeable with a {@code SLICE} if
 * it contains only integer values. A {@code FRAME} of size 1x1 can be
 * treated as a {@code NUM} (or {@code STR}), and vice versa. In order to
 * support conversions like this there is a set of methods
 * <pre>{@code
 *    maybeX()
 *    getX()
 * }</pre>
 * for various kinds of {@code X}s. Each subclass of {@code Val} overrides
 * one or more of these methods to indicate whether it is convertible to type
 * {@code X}, and to return raw value of type {@code X}.
 *
 * <p> The conversions among types do not follow a rigid structure, and are
 * not hierarchical. In particular, there are values that may convert
 * in both directions, there are values that convert only unidirectionally.
 * It is possible that in the future the set of allowable conversions will be
 * extended (or on the contrary, restricted). Generally you should use a
 * {@code maybeX()} call when you expect to obtain an argument convertible to
 * some particular type. If on the other hand your code needs to handle
 * different types differently, then either switch on the value's
 * {@link #type()} or perform the {@code maybeX()} / {@code maybeY()} calls in
 * a carefully chosen order, bearing in mind that these checks are not
 * exclusive.
 *
 * <p> NOTE: This class should *not* derive from Iced, since these objects
 * are not intended to be passed around the cluster.
 */
public abstract class Val {

  /**
   * Return {@link Type} of this {@code Val}.
   *
   * <p>Each subclass of {@code Val} is associated with a unique constant
   * within the {@code Val.Type} enum. This is roughly equivalent to
   * {@code instanceof} expressions, however may be more convenient to use
   * within switch statements or for serialization.
   */
  public abstract Type type();

  public enum Type {
    NULL,     // null (void) value
    NUM,      // double
    NUMS,     // array of doubles
    SLICE,    // list of slices -- used for indexing
    STR,      // string
    STRS,     // array of strings
    IDS,      // list of unevaluated variables
    FRAME,    // Frame object
    WFRAME,   // WorkFrame
    FUNC,     // function -- either built-in or user-defined
  }

  //--------------------------------------------------------------------------------------------------------------------
  // maybeX()/getX() methods
  //--------------------------------------------------------------------------------------------------------------------

  public boolean maybeNum() {
    return false;
  }
  public boolean maybeInt() {
    return false;
  }
  public boolean maybeNums() {
    return false;
  }
  public boolean maybeSlice() {
    return false;
  }
  public boolean maybeStr() {
    return false;
  }
  public boolean maybeStrs() {
    return false;
  }
  public boolean maybeIds() {
    return false;
  }
  public boolean maybeFrame() {
    return false;
  }
  public boolean maybeFunc() {
    return false;
  }

  public double getNum() {
    throw badValue("number");
  }
  public int getInt() {
    throw badValue("integer");
  }
  public double[] getNums() {
    throw badValue("number array");
  }
  public SliceList getSlice() {
    throw badValue("slice list");
  }
  public String getStr() {
    throw badValue("string");
  }
  public String[] getStrs() {
    throw badValue("string array");
  }
  public IdList getIds() {
    throw badValue("list of ids (in backticks)");
  }
  public Frame getFrame() {
    throw badValue("Frame");
  }
  public Function getFun() {
    throw badValue("function");
  }

  
  private IllegalArgumentException badValue(String expectedType) {
    return new IllegalArgumentException("Expected a " + expectedType + " but found a " + getClass());
  }
}
