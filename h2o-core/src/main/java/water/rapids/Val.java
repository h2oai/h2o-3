package water.rapids;

import water.fvec.Frame;
import water.Iced;

/** Generic execution values for the untyped stack */
abstract public class Val extends Iced {
  // Things on the execution stack
  final public static int NUM = 1;     // scalar
  final public static int STR = 2;     // string scalar
  final public static int FRM = 3;     // Frame, not a Vec.  Can be a Frame of 1 Vec
  final public static int ROW = 4;     // Row of data; limited to a single array of doubles
  final public static int FUN = 5;     // Function

  abstract public int type();
  boolean isNum() { return false; }
  boolean isStr() { return false; }
  boolean isFrame() { return false; }
  boolean isRow() { return false; }
  boolean isFun() { return false; }

  public double getNum() { throw new IllegalArgumentException("Expected a number but found a "+getClass()); }
  public String getStr() { throw new IllegalArgumentException("Expected a String but found a "+getClass()); }
  public Frame  getFrame(){throw new IllegalArgumentException("Expected a Frame but found a "+getClass()); }
  public double[]getRow(){ throw new IllegalArgumentException("Expected a Row but found a "+getClass()); }
  public AST    getFun() { throw new IllegalArgumentException("Expected a function but found a "+getClass()); }
}

class ValNum extends Val {
  final double _d;
  ValNum(double d) { _d = d; }
  @Override public String toString() { return ""+_d; }
  @Override public int type () { return NUM; }
  @Override boolean isNum() { return true; }
  @Override public double getNum() { return _d; }
}

class ValStr extends Val {
  final String _str;
  ValStr(String str) { _str = str; }
  @Override public String toString() { return '"'+_str+'"'; }
  @Override public int type () { return STR; }
  @Override boolean isStr() { return true; }
  @Override public String getStr() { return _str; }
}

class ValFrame extends Val {
  final Frame _fr;
  ValFrame(Frame fr) { _fr = fr; }
  @Override public String toString() { return _fr.toString(); }
  @Override public int type () { return FRM; }
  @Override boolean isFrame() { return true; }
  @Override public Frame getFrame() { return _fr; }
}

class ValRow extends Val {
  final double[] _ds;
  ValRow(double[] ds) { _ds = ds; }
  @Override public String toString() { return java.util.Arrays.toString(_ds); }
  @Override public int type () { return ROW; }
  @Override boolean isRow() { return true; }
  @Override public double[] getRow() { return _ds; }
}

class ValFun extends Val {
  final AST _ast;
  ValFun(AST ast) { _ast = ast; }
  @Override public String toString() { return _ast.toString(); }
  @Override public int type () { return FUN; }
  @Override boolean isFun() { return true; }
  @Override public AST getFun() { return _ast; }
  public String[] getArgs() { return ((ASTPrim)_ast).args(); }
}
