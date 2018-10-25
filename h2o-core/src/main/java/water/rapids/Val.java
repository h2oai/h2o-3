package water.rapids;

import hex.Model;
import water.fvec.Frame;
import water.Iced;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.Map;

/**
 * Generic execution values for the untyped stack.
 */
abstract public class Val extends Iced {
  // Things on the execution stack
  final public static int NUM = 1;     // double
  final public static int NUMS = 2;    // array of doubles
  final public static int STR = 3;     // string
  final public static int STRS = 4;    // array of strings
  final public static int FRM = 5;     // Frame, not a Vec.  Can be a Frame of 1 Vec
  final public static int ROW = 6;     // Row of data; limited to a single array of doubles
  final public static int FUN = 7;     // Function
  final public static int MOD = 8;     // Model
  final public static int MFRM = 9;     // Map of (String, Frame)


  abstract public int type();

  // One of these methods is overridden in each subclass
  public boolean isNum()   { return false; }
  public boolean isNums()  { return false; }
  public boolean isStr()   { return false; }
  public boolean isStrs()  { return false; }
  public boolean isFrame() { return false; }
  public boolean isMapFrame() { return false; }
  public boolean isRow()   { return false; }
  public boolean isFun()   { return false; }
  public boolean isModel() { return false; }

  // One of these methods is overridden in each subclass
  public double   getNum()   { throw badValue("number"); }
  public double[] getNums()  { throw badValue("number array"); }
  public String   getStr()   { throw badValue("String"); }
  public String[] getStrs()  { throw badValue("String array"); }
  public Frame    getFrame() { throw badValue("Frame"); }
  public Map<String, Frame> getMapFrame() { throw badValue("MapFrame"); }
  public double[] getRow()   { throw badValue("Row"); }
  public AstPrimitive getFun() { throw badValue("function"); }
  public Model    getModel() { throw badValue("Model"); }

  private IllegalArgumentException badValue(String expectedType) {
    return new IllegalArgumentException("Expected a " + expectedType + " but found a " + getClass());
  }
}
