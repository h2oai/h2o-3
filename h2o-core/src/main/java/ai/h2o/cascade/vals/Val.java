package ai.h2o.cascade.vals;

import ai.h2o.cascade.asts.Ast;
import ai.h2o.cascade.core.WorkFrame;
import ai.h2o.cascade.core.Function;
import ai.h2o.cascade.core.SliceList;


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
 * <p> In rare circumstances values of different types may be interchangeable.
 * For example, a {@code NULL} value may be used in place of almost all other
 * values. A {@code NUM} may be treated as an {@code INT} or {@code BOOL}. In
 * order to support these intricacies, the following functions are available:
 * <pre>{@code
 *    isX()
 *    getX()
 * }</pre>
 * for various kinds of {@code X}s. Each subclass of {@code Val} overrides
 * one or more of these methods to indicate whether it is convertible to type
 * {@code X}, and to return raw value of type {@code X}.
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
    NULL("ValNull"),     // null (void) value
    NUM("ValNum"),       // double
    INT("ValNum"),       // integer (backed by the ValNum class)
    BOOL("ValNum"),      // boolean (backed by the ValNum class)
    NUMS("ValNums"),     // array of doubles
    SLICE("ValSlice"),   // list of slices -- used for indexing
    STR("ValStr"),       // string
    STRS("ValStrs"),     // array of strings
    IDS("IdList"),       // list of unevaluated variables
    FRAME("ValFrame"),   // Frame object
    FUN("ValFun"),       // function -- either built-in or user-defined
    AST("ValAst");       // unevaluated AST

    private String valClassName;
    Type(String name) {
      valClassName = name;
    }
    public String getValClassName() {
      return valClassName;
    }
  }


  //--------------------------------------------------------------------------------------------------------------------
  // isX()/getX() methods
  //--------------------------------------------------------------------------------------------------------------------

  public boolean isNum() {
    return false;
  }
  public boolean isInt() {
    return false;
  }
  public boolean isBool() {
    return false;
  }
  public boolean isNums() {
    return false;
  }
  public boolean isSlice() {
    return false;
  }
  public boolean isStr() {
    return false;
  }
  public boolean isStrs() {
    return false;
  }
  public boolean isIds() {
    return false;
  }
  public boolean isFrame() {
    return false;
  }
  public boolean isFun() {
    return false;
  }
  public boolean isAst() {
    return false;
  }


  public double getNum() {
    throw badValue("number");
  }
  public int getInt() {
    throw badValue("integer");
  }
  public boolean getBool() {
    throw badValue("boolean");
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
  public WorkFrame getFrame() {
    throw badValue("Frame");
  }
  public Function getFun() {
    throw badValue("function");
  }
  public Ast getAst() {
    throw badValue("unevaluated expression");
  }


  private IllegalArgumentException badValue(String expectedType) {
    return new IllegalArgumentException("Expected a " + expectedType + " but found a " + getClass());
  }
}
