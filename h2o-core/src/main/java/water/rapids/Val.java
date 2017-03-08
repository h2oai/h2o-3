package water.rapids;

import water.Iced;
import water.fvec.Frame;
import water.rapids.ast.AstPrimitive;

/**
 * Generic execution values for the untyped stack.
 */
public abstract class Val extends Iced {

  // Things on the execution stack
  public static final int NULL = 0;    // null
  public static final int NUM = 1;     // double
  public static final int NUMS = 2;    // array of doubles
  public static final int STR = 3;     // string
  public static final int STRS = 4;    // array of strings
  public static final int FRM = 5;     // Frame, not a Vec.  Can be a Frame of 1 Vec
  public static final int ROW = 6;     // Row of data; limited to a single array of doubles
  public static final int FUN = 7;     // Function

  public abstract int type();

  // One of these methods is overridden in each subclass
  public boolean isNum()   { return false; }
  public boolean isNums()  { return false; }
  public boolean isStr()   { return false; }
  public boolean isStrs()  { return false; }
  public boolean isFrame() { return false; }
  public boolean isRow()   { return false; }
  public boolean isFun()   { return false; }

  // One of these methods is overridden in each subclass
  public double   getNum()   { throw badValue("number"); }
  public double[] getNums()  { throw badValue("number array"); }
  public String   getStr()   { throw badValue("String"); }
  public String[] getStrs()  { throw badValue("String array"); }
  public Frame    getFrame() { throw badValue("Frame"); }
  public double[] getRow()   { throw badValue("Row"); }
  public AstPrimitive getFun() { throw badValue("function"); }

  private IllegalArgumentException badValue(String expectedType) {
    return new IllegalArgumentException("Expected a " + expectedType + " but found a " + getClass());
  }
}
