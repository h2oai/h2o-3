package water.rapids;

import water.fvec.Frame;
import water.Iced;

import java.util.Arrays;

/** Generic execution values for the untyped stack */
abstract public class Val extends Iced {
  // Things on the execution stack
  final public static int NUM = 1;     // double
  final public static int NUMS= 2;     // array of doubles
  final public static int STR = 3;     // string
  final public static int STRS= 4;     // array of strings
  final public static int FRM = 5;     // Frame, not a Vec.  Can be a Frame of 1 Vec
  final public static int ROW = 6;     // Row of data; limited to a single array of doubles
  final public static int FUN = 7;     // Function

  abstract public int type();
  boolean isNum() { return false; }
  boolean isNums(){ return false; }
  boolean isStr() { return false; }
  boolean isStrs(){ return false; }
  boolean isFrame(){return false; }
  boolean isRow() { return false; }
  boolean isFun() { return false; }

  public double   getNum() { throw new IllegalArgumentException("Expected a number but found a "+getClass()); }
  public double[] getNums(){ throw new IllegalArgumentException("Expected a number array but found a "+getClass()); }
  public String   getStr() { throw new IllegalArgumentException("Expected a String but found a "+getClass()); }
  public String[] getStrs(){ throw new IllegalArgumentException("Expected a String array but found a "+getClass()); }
  public Frame    getFrame(){throw new IllegalArgumentException("Expected a Frame but found a "+getClass()); }
  public double[] getRow() { throw new IllegalArgumentException("Expected a Row but found a "+getClass()); }
  public AST      getFun() { throw new IllegalArgumentException("Expected a function but found a "+getClass()); }
}

class ValNum extends Val {
  final double _d;
  ValNum(double d) { _d = d; }
  @Override public String toString() { return ""+_d; }
  @Override public int type () { return NUM; }
  @Override boolean isNum() { return true; }
  @Override public double getNum() { return _d; }
}

class ValNums extends Val {
  final double[] _ds;
  ValNums(double[] ds) { _ds = ds; }
  @Override public String toString() { return Arrays.toString(_ds); }
  @Override public int type () { return NUMS; }
  @Override boolean isNums() { return true; }
  @Override public double[] getNums() { return _ds; }
}

class ValStr extends Val {
  final String _str;
  ValStr(String str) { _str = str; }
  @Override public String toString() { return '"'+_str+'"'; }
  @Override public int type () { return STR; }
  @Override boolean isStr() { return true; }
  @Override public String getStr() { return _str; }
}

class ValStrs extends Val {
  final String[] _strs;
  ValStrs(String[] strs) { _strs = strs; }
  @Override public String toString() { return Arrays.toString(_strs); }
  @Override public int type () { return STRS; }
  @Override boolean isStrs() { return true; }
  @Override public String[] getStrs() { return _strs; }
}

class ValFrame extends Val {
  final Frame _fr;
  ValFrame(Frame fr) { assert( fr!= null ); _fr = fr; }
  @Override public String toString() { return _fr.toString(); }
  @Override public int type () { return FRM; }
  @Override boolean isFrame() { return true; }
  @Override public Frame getFrame() { return _fr; }
}

class ValRow extends Val {
  final double[] _ds;
  final String[] _names;
  ValRow(double[] ds, String[] names) { _ds = ds; _names=names; }
  @Override public String toString() { return java.util.Arrays.toString(_ds); }
  @Override public int type () { return ROW; }
  @Override boolean isRow() { return true; }
  @Override public double[] getRow() { return _ds; }
  public String[] getNames() { return _names; }

  ValRow slice(int[] cols) {
    double[] ds = new double[cols.length];
    String[] ns = new String[cols.length];
    for(int i=0;i<cols.length;++i) {
      ds[i] = _ds[cols[i]];
      ns[i] = _names[cols[i]];
    }
    return new ValRow(ds,ns);
  }
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
