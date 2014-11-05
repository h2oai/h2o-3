package water.cascade;

//import hex.Quantiles;

import water.*;
import water.api.QuantilesHandler.Quantiles;
import water.fvec.*;
import water.util.ArrayUtils;
import water.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;

//import hex.la.Matrix;
//import org.apache.commons.math3.util.*;
//import org.joda.time.DateTime;
//import org.joda.time.MutableDateTime;
//import water.cascade.Env;
//import water.fvec.Vec.VectorGroup;
//import water.util.Log;
//import water.util.Utils;

/**
 * Parse a generic R string and build an AST, in the context of an H2O Cloud
 */

// --------------------------------------------------------------------------
public abstract class ASTOp extends AST {
  // Tables of operators by arity
  static final public HashMap<String,ASTOp> UNI_INFIX_OPS  = new HashMap<>();
  static final public HashMap<String,ASTOp> BIN_INFIX_OPS  = new HashMap<>();
  static final public HashMap<String,ASTOp> PREFIX_OPS     = new HashMap<>();
  static final public HashMap<String,ASTOp> UDF_OPS        = new HashMap<>();
  static final public HashMap<String, AST>  SYMBOLS        = new HashMap<>();
  // Too avoid a cyclic class-loading dependency, these are init'd before subclasses.
  static final String VARS1[] = new String[]{ "", "x"};
  static final String VARS2[] = new String[]{ "", "x","y"};
  static {
    // All of the special chars (see Exec.java)
    SYMBOLS.put("=", new ASTAssign());
    SYMBOLS.put("'", new ASTString('\'', ""));
    SYMBOLS.put("\"",new ASTString('\"', ""));
    SYMBOLS.put("$", new ASTId('$', ""));
    SYMBOLS.put("!", new ASTId('!', ""));
    SYMBOLS.put("#", new ASTNum(0));
    SYMBOLS.put("g", new ASTGT());
    SYMBOLS.put("G", new ASTGE());
    SYMBOLS.put("l", new ASTLT());
    SYMBOLS.put("L", new ASTLE());
    SYMBOLS.put("N", new ASTNE());
    SYMBOLS.put("n", new ASTEQ());
    SYMBOLS.put("[", new ASTSlice());
    SYMBOLS.put("{", new ASTSeries(null, null));
    SYMBOLS.put(":", new ASTSpan(new ASTNum(0),new ASTNum(0)));
    SYMBOLS.put("_", new ASTNot());
    SYMBOLS.put("if", new ASTIf());
    SYMBOLS.put("else", new ASTElse());
    SYMBOLS.put("for", new ASTFor());
    SYMBOLS.put("while", new ASTWhile());
    SYMBOLS.put("return", new ASTReturn());

    //TODO: Have `R==` type methods (also `py==`, `js==`, etc.)

    // Unary infix ops
    putUniInfix(new ASTNot());
    // Binary infix ops
    putBinInfix(new ASTPlus());
    putBinInfix(new ASTSub());
    putBinInfix(new ASTMul());
    putBinInfix(new ASTDiv());
    putBinInfix(new ASTPow());
    putBinInfix(new ASTPow2());
    putBinInfix(new ASTMod());
    putBinInfix(new ASTAND());
    putBinInfix(new ASTOR());
    putBinInfix(new ASTLT());
    putBinInfix(new ASTLE());
    putBinInfix(new ASTGT());
    putBinInfix(new ASTGE());
    putBinInfix(new ASTEQ());
    putBinInfix(new ASTNE());
    putBinInfix(new ASTLA());
    putBinInfix(new ASTLO());

    // Unary prefix ops
    putPrefix(new ASTIsNA());
    putPrefix(new ASTNrow());
    putPrefix(new ASTNcol());
    putPrefix(new ASTLength());
    putPrefix(new ASTAbs ());
    putPrefix(new ASTSgn ());
    putPrefix(new ASTSqrt());
    putPrefix(new ASTCeil());
    putPrefix(new ASTFlr ());
    putPrefix(new ASTLog ());
    putPrefix(new ASTExp ());
    putPrefix(new ASTScale());
    putPrefix(new ASTFactor());
    putPrefix(new ASTIsFactor());
    putPrefix(new ASTAnyFactor());              // For Runit testing
    putPrefix(new ASTCanBeCoercedToLogical());
    putPrefix(new ASTAnyNA());
    putPrefix(new ASTRound());
    putPrefix(new ASTSignif());
    putPrefix(new ASTTrun());

    // Trigonometric functions
    putPrefix(new ASTCos());
    putPrefix(new ASTSin());
    putPrefix(new ASTTan());
    putPrefix(new ASTACos());
    putPrefix(new ASTASin());
    putPrefix(new ASTATan());
    putPrefix(new ASTCosh());
    putPrefix(new ASTSinh());
    putPrefix(new ASTTanh());

    // More generic reducers
    putPrefix(new ASTMin ());
    putPrefix(new ASTMax ());
    putPrefix(new ASTSum ());
    putPrefix(new ASTSdev());
    putPrefix(new ASTVar());
    putPrefix(new ASTMean());

    // Misc
    putPrefix(new ASTMatch());
    putPrefix(new ASTRename());  //TODO
    putPrefix(new ASTSeq   ());  //TODO
    putPrefix(new ASTSeqLen());  //TODO
    putPrefix(new ASTRepLen());  //TODO
    putPrefix(new ASTQtile ());  //TODO
    putPrefix(new ASTCbind ());
    putPrefix(new ASTTable ());
//    putPrefix(new ASTReduce());
//    putPrefix(new ASTIfElse());
//    putPrefix(new ASTRApply());
//    putPrefix(new ASTSApply());
//    putPrefix(new ASTddply ());
//    putPrefix(new ASTUnique());
//    putPrefix(new ASTXorSum ());
    putPrefix(new ASTRunif ());
    putPrefix(new ASTCut   ());
    putPrefix(new ASTLs    ());

//Classes that may not come back:

//    putPrefix(new ASTfindInterval());
//    putPrefix(new ASTPrint ());
//    putPrefix(new ASTCat   ());
//Time extractions, to and from msec since the Unix Epoch
//    putPrefix(new ASTYear  ());
//    putPrefix(new ASTMonth ());
//    putPrefix(new ASTDay   ());
//    putPrefix(new ASTHour  ());
//    putPrefix(new ASTMinute());
//    putPrefix(new ASTSecond());
//    putPrefix(new ASTMillis());
//
//    // Time series operations
//    putPrefix(new ASTDiff  ());
//    putPrefix(new ASTIsTRUE());
//    putPrefix(new ASTMTrans());
//    putBinInfix(new ASTMMult());


  }
  static private void putUniInfix(ASTOp ast) { UNI_INFIX_OPS.put(ast.opStr(),ast); }
  static private void putBinInfix(ASTOp ast) { BIN_INFIX_OPS.put(ast.opStr(),ast); SYMBOLS.put(ast.opStr(), ast); }
  static private void putPrefix  (ASTOp ast) { PREFIX_OPS.put(ast.opStr(),ast);    SYMBOLS.put(ast.opStr(), ast); }
  static         void putUDF     (ASTOp ast, String fn) { UDF_OPS.put(fn, ast);    SYMBOLS.put(fn, ast);}
  static         void removeUDF  (String fn) { UDF_OPS.remove(fn); }
  static public ASTOp isOp(String id) {
    // This order matters. If used as a prefix OP, `+` and `-` are binary only.
    ASTOp op4 = UDF_OPS.get(id);
    if( op4 != null ) return op4;
    return isBuiltinOp(id);
  }
  static public ASTOp isBuiltinOp(String id) {
    ASTOp op3 = PREFIX_OPS.get(id); if( op3 != null ) return op3;
    ASTOp op2 = BIN_INFIX_OPS.get(id); if( op2 != null ) return op2;
    return UNI_INFIX_OPS.get(id);
  }
  static public boolean isInfixOp(String id) { return BIN_INFIX_OPS.containsKey(id) || UNI_INFIX_OPS.containsKey(id); }
  static public boolean isUDF(String id) { return UDF_OPS.containsKey(id); }
  static public boolean isUDF(ASTOp op) { return isUDF(op.opStr()); }
  static public Set<String> opStrs() {
    Set<String> all = UNI_INFIX_OPS.keySet();
    all.addAll(BIN_INFIX_OPS.keySet());
    all.addAll(PREFIX_OPS.keySet());
    all.addAll(UDF_OPS.keySet());
    return all;
  }

  // All fields are final, because functions are immutable
  final String _vars[]; // Variable names
  ASTOp( String vars[]) { _vars = vars; }

  abstract String opStr();
  abstract ASTOp  make();
  // Standard column-wise function application
  abstract void apply(Env e);
  // Special row-wise 'apply'
  double[] map(Env env, double[] in, double[] out) { throw H2O.unimpl(); }
  @Override void exec(Env e) { throw H2O.fail(); }
  @Override int type() { throw H2O.fail(); }
  @Override String value() { throw H2O.fail(); }

//  @Override public String toString() {
//    String s = _t._ts[0]+" "+opStr()+"(";
//    int len=_t._ts.length;
//    for( int i=1; i<len-1; i++ )
//      s += _t._ts[i]+" "+(_vars==null?"":_vars[i])+", ";
//    return s + (len > 1 ? _t._ts[len-1]+" "+(_vars==null?"":_vars[len-1]) : "")+")";
//  }
//  public String toString(boolean verbose) {
//    if( !verbose ) return toString(); // Just the fun name& arg names
//    return toString();
//  }

  public static ASTOp get(String op) {
    if (BIN_INFIX_OPS.containsKey(op)) return BIN_INFIX_OPS.get(op);
    if (UNI_INFIX_OPS.containsKey(op)) return UNI_INFIX_OPS.get(op);
    if (isUDF(op)) return UDF_OPS.get(op);
    if (PREFIX_OPS.containsKey(op)) return PREFIX_OPS.get(op);
    throw H2O.fail("Unimplemented: Could not find the operation or function "+op);
  }
}

abstract class ASTUniOp extends ASTOp {
  ASTUniOp() { super(VARS1); }
  double op( double d ) { throw H2O.fail(); }
  protected ASTUniOp( String[] vars) { super(vars); }
  ASTUniOp parse_impl(Exec E) {
    AST arg = E.parse();
    ASTUniOp res = (ASTUniOp) clone();
    res._asts = new AST[]{arg};
    return res;
  }
  @Override void apply(Env env) {
    // Expect we can broadcast across all functions as needed.
    if( env.isNum() ) { env.push(new ValNum(op(env.popDbl()))); return; }
//    if( env.isStr() ) { env.push(new ASTString(op(env.popStr()))); return; }
    Frame fr = env.pop0Ary();
    final ASTUniOp uni = this;  // Final 'this' so can use in closure
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk[] chks, NewChunk[] nchks ) {
        for( int i=0; i<nchks.length; i++ ) {
          NewChunk n =nchks[i];
          Chunk c = chks[i];
          int rlen = c._len;
          if (c.vec().isEnum() || c.vec().isUUID() || c.vec().isString()) {
            for (int r = 0; r <rlen;r++) n.addNum(Double.NaN);
          } else {
            for( int r=0; r<rlen; r++ )
              n.addNum(uni.op(c.at0(r)));
          }
        }
      }
    }.doAll(fr.numCols(),fr).outputFrame(Key.make(), fr._names, null);
    env.cleanup(fr);
    env.push(new ValFrame(fr2));
  }
}

abstract class ASTUniPrefixOp extends ASTUniOp {
  ASTUniPrefixOp( ) { super(); }
  ASTUniPrefixOp( String[] vars) { super(vars); }
}

class ASTCos  extends ASTUniPrefixOp { @Override String opStr(){ return "cos";  } @Override ASTOp make() {return new ASTCos ();} @Override double op(double d) { return Math.cos(d);}}
class ASTSin  extends ASTUniPrefixOp { @Override String opStr(){ return "sin";  } @Override ASTOp make() {return new ASTSin ();} @Override double op(double d) { return Math.sin(d);}}
class ASTTan  extends ASTUniPrefixOp { @Override String opStr(){ return "tan";  } @Override ASTOp make() {return new ASTTan ();} @Override double op(double d) { return Math.tan(d);}}
class ASTACos extends ASTUniPrefixOp { @Override String opStr(){ return "acos"; } @Override ASTOp make() {return new ASTACos();} @Override double op(double d) { return Math.acos(d);}}
class ASTASin extends ASTUniPrefixOp { @Override String opStr(){ return "asin"; } @Override ASTOp make() {return new ASTASin();} @Override double op(double d) { return Math.asin(d);}}
class ASTATan extends ASTUniPrefixOp { @Override String opStr(){ return "atan"; } @Override ASTOp make() {return new ASTATan();} @Override double op(double d) { return Math.atan(d);}}
class ASTCosh extends ASTUniPrefixOp { @Override String opStr(){ return "cosh"; } @Override ASTOp make() {return new ASTCosh ();} @Override double op(double d) { return Math.cosh(d);}}
class ASTSinh extends ASTUniPrefixOp { @Override String opStr(){ return "sinh"; } @Override ASTOp make() {return new ASTSinh ();} @Override double op(double d) { return Math.sinh(d);}}
class ASTTanh extends ASTUniPrefixOp { @Override String opStr(){ return "tanh"; } @Override ASTOp make() {return new ASTTanh ();} @Override double op(double d) { return Math.tanh(d);}}
class ASTAbs  extends ASTUniPrefixOp { @Override String opStr(){ return "abs";  } @Override ASTOp make() {return new ASTAbs ();} @Override double op(double d) { return Math.abs(d);}}
class ASTSgn  extends ASTUniPrefixOp { @Override String opStr(){ return "sgn" ; } @Override ASTOp make() {return new ASTSgn ();} @Override double op(double d) { return Math.signum(d);}}
class ASTSqrt extends ASTUniPrefixOp { @Override String opStr(){ return "sqrt"; } @Override ASTOp make() {return new ASTSqrt();} @Override double op(double d) { return Math.sqrt(d);}}
class ASTTrun extends ASTUniPrefixOp { @Override String opStr(){ return "trunc"; } @Override ASTOp make() {return new ASTTrun();} @Override double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}
class ASTCeil extends ASTUniPrefixOp { @Override String opStr(){ return "ceil"; } @Override ASTOp make() {return new ASTCeil();} @Override double op(double d) { return Math.ceil(d);}}
class ASTFlr  extends ASTUniPrefixOp { @Override String opStr(){ return "floor";} @Override ASTOp make() {return new ASTFlr ();} @Override double op(double d) { return Math.floor(d);}}
class ASTLog  extends ASTUniPrefixOp { @Override String opStr(){ return "log";  } @Override ASTOp make() {return new ASTLog ();} @Override double op(double d) { return Math.log(d);}}
class ASTExp  extends ASTUniPrefixOp { @Override String opStr(){ return "exp";  } @Override ASTOp make() {return new ASTExp ();} @Override double op(double d) { return Math.exp(d);}}

class ASTIsNA extends ASTUniPrefixOp { @Override String opStr(){ return "is.na";} @Override ASTOp make() { return new ASTIsNA();} @Override double op(double d) { return Double.isNaN(d)?1:0;}
  @Override void apply(Env env) {
    // Expect we can broadcast across all functions as needed.
    if( env.isNum() ) { env.push(new ValNum(op(env.popDbl()))); return; }
    //if( env.isStr() ) { env.push(new ASTString(op(env.popStr()))); return; }
    Frame fr = env.pop0Ary();
    final ASTUniOp uni = this;  // Final 'this' so can use in closure
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        for( int i=0; i<nchks.length; i++ ) {
          NewChunk n = nchks[i];
          Chunk c = chks[i];
          int rlen = c._len;
          for( int r=0; r<rlen; r++ )
            n.addNum( c.isNA0(r) ? 1 : 0);
        }
      }
    }.doAll(fr.numCols(),fr).outputFrame(Key.make(), fr._names, null);
    env.cleanup(fr);
    env.push(new ValFrame(fr2));
  }
}

class ASTRound extends ASTUniPrefixOp {
  int _digits = 0;
  @Override String opStr() { return "round"; }
  ASTRound() { super(new String[]{"round", "x", "digits"}); }
  @Override ASTRound parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // Get the digits
    _digits = (int)((ASTNum)(E.skipWS().parse())).dbl();
    ASTRound res = (ASTRound) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override ASTOp make() { return this; }
  @Override void apply(Env env) {
    final int digits = _digits;
    if(env.isAry()) {
      Frame fr = env.pop0Ary();
      for(int i = 0; i < fr.vecs().length; i++) {
        if(fr.vecs()[i].isEnum())
          throw new IllegalArgumentException("Non-numeric column " + String.valueOf(i+1) + " in data frame");
      }
      Frame fr2 = new MRTask() {
        @Override public void map(Chunk chks[], NewChunk nchks[]) {
          for(int i = 0; i < nchks.length; i++) {
            NewChunk n = nchks[i];
            Chunk c = chks[i];
            int rlen = c._len;
            for(int r = 0; r < rlen; r++)
              n.addNum(roundDigits(c.at0(r),digits));
          }
        }
      }.doAll(fr.numCols(),fr).outputFrame(fr.names(),fr.domains());
      env.cleanup(fr);
      env.push(new ValFrame(fr2));
    }
    else
      env.push(new ValNum(roundDigits(env.popDbl(), digits)));
  }

  // e.g.: floor(2.676*100 + 0.5) / 100 => 2.68
  static double roundDigits(double x, int digits) {
    if(Double.isNaN(x)) return x;
    return Math.floor(x * digits + 0.5) / (double)digits;
  }
}

class ASTSignif extends ASTUniPrefixOp {
  int _digits = 6;  // R default
  @Override String opStr() { return "signif"; }
  ASTSignif() { super(new String[]{"signif", "x", "digits"}); }
  @Override ASTRound parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // Get the digits
    _digits = (int)((ASTNum)(E.skipWS().parse())).dbl();
    ASTRound res = (ASTRound) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override ASTOp make() { return this; }
  @Override void apply(Env env) {
    final int digits = _digits;
    if(digits < 0)
      throw new IllegalArgumentException("Error in signif: argument digits must be a non-negative integer");

    if(env.isAry()) {
      Frame fr = env.pop0Ary();
      for(int i = 0; i < fr.vecs().length; i++) {
        if(fr.vecs()[i].isEnum())
          throw new IllegalArgumentException("Non-numeric column " + String.valueOf(i+1) + " in data frame");
      }
      Frame fr2 = new MRTask() {
        @Override public void map(Chunk chks[], NewChunk nchks[]) {
          for(int i = 0; i < nchks.length; i++) {
            NewChunk n = nchks[i];
            Chunk c = chks[i];
            int rlen = c._len;
            for(int r = 0; r < rlen; r++)
              n.addNum(signifDigits(c.at0(r),digits));
          }
        }
      }.doAll(fr.numCols(),fr).outputFrame(fr.names(),fr.domains());
      env.cleanup(fr);
      env.push(new ValFrame(fr2));
    }
    else
      env.push(new ValNum(signifDigits(env.popDbl(), digits)));
  }
  static double signifDigits(double x, int digits) {
    if(Double.isNaN(x)) return x;
    BigDecimal bd = new BigDecimal(x);
    bd = bd.round(new MathContext(digits, RoundingMode.HALF_EVEN));
    return bd.doubleValue();
  }
}

class ASTNrow extends ASTUniPrefixOp {
  ASTNrow() { super(VARS1); }
  @Override String opStr() { return "nrow"; }
  @Override ASTOp make() {return this;}
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    double d = fr.numRows();
    env.cleanup(fr);
    env.push(new ValNum(d));
  }
}

class ASTNcol extends ASTUniPrefixOp {
  ASTNcol() { super(VARS1); }
  @Override String opStr() { return "ncol"; }
  @Override ASTOp make() {return this;}
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    double d = fr.numCols();
    env.cleanup(fr);
    env.push(new ValNum(d));
  }
}

class ASTLength extends ASTUniPrefixOp {
  ASTLength() { super(VARS1); }
  @Override String opStr() { return "length"; }
  @Override ASTOp make() { return this; }
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    double d = fr.numCols() == 1 ? fr.numRows() : fr.numCols();
    env.cleanup(fr);
    env.push(new ValNum(d));
  }
}

class ASTIsFactor extends ASTUniPrefixOp {
  ASTIsFactor() { super(VARS1); }
  @Override String opStr() { return "is.factor"; }
  @Override ASTOp make() {return this;}
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    String res = "TRUE";
    if (fr.numCols() != 1) throw new IllegalArgumentException("is.factor applies to a single column.");
    if (fr.anyVec().isEnum()) res = "FALSE";
    env.cleanup(fr);
    env.push(new ValStr(res));
  }
}

// Added to facilitate Runit testing
class ASTAnyFactor extends ASTUniPrefixOp {
  ASTAnyFactor() { super(VARS1);}
  @Override String opStr() { return "any.factor"; }
  @Override ASTOp make() {return this;}
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    String res = "FALSE";
    for (int i = 0; i < fr.vecs().length; ++i)
      if (fr.vecs()[i].isEnum()) { res = "TRUE"; break; }
    env.cleanup(fr);
    env.push(new ValStr(res));
  }
}

class ASTCanBeCoercedToLogical extends ASTUniPrefixOp {
  ASTCanBeCoercedToLogical() { super(VARS1); }
  @Override String opStr() { return "canBeCoercedToLogical"; }
  @Override ASTOp make() {return this;}
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    String res = "FALSE";
    Vec[] v = fr.vecs();
    for (Vec aV : v)
      if (aV.isInt())
        if (aV.min() == 0 && aV.max() == 1) { res = "TRUE"; break; }
    env.cleanup(fr);
    env.push(new ValStr(res));
  }
}

class ASTAnyNA extends ASTUniPrefixOp {
  ASTAnyNA() { super(VARS1); }
  @Override String opStr() { return "any.na"; }
  @Override ASTOp make() {return this;}
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    String res = "FALSE";
    for (int i = 0; i < fr.vecs().length; ++i)
      if (fr.vecs()[i].naCnt() > 0) { res = "TRUE"; break; }
    env.cleanup(fr);
    env.push(new ValStr(res));
  }
}
//
//class ASTIsTRUE extends ASTUniPrefixOp {
//  ASTIsTRUE() {super(VARS1,new Type[]{Type.DBL,Type.unbound()});}
//  @Override String opStr() { return "isTRUE"; }
//  @Override ASTOp make() {return new ASTIsTRUE();}  // to make sure fcn get bound at each new context
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    double res = env.isDbl() && env.popDbl()==1.0 ? 1:0;
//    env.pop();
//    env.poppush(res);
//  }
//}

class ASTScale extends ASTUniPrefixOp {
  boolean _center;
  double[] _centers;
  boolean _scale;
  double[] _scales;
  ASTScale() { super(new String[]{"ary", "center", "scale"});}
  @Override String opStr() { return "scale"; }
  @Override ASTOp make() {return this;}
  ASTScale parse_impl(Exec E) {
    AST ary = E.parse();
    parseArg(E, true);  // centers parse
    parseArg(E, false); // scales parse
    ASTScale res = (ASTScale) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  private void parseArg(Exec E, boolean center) {
    if (center) {
      String[] centers = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
      if (centers == null) {
        // means `center` is boolean
        AST a = E._env.lookup((ASTId)E.skipWS().parse());
        _center = ((ASTNum)a).dbl() == 1;
        _centers = null;
      } else {
        for (int i = 0; i < centers.length; ++i) centers[i] = centers[i].replace("\"", "").replace("\'", "");
        _centers = new double[centers.length];
        for (int i = 0; i < centers.length; ++i) _centers[i] = Double.valueOf(centers[i]);
      }
    } else {
      String[] centers = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
      if (centers == null) {
        // means `scale` is boolean
        AST a = E._env.lookup((ASTId)E.skipWS().parse());
        _scale = ((ASTNum)a).dbl() == 1;
        _scales = null;
      } else {
        for (int i = 0; i < centers.length; ++i) centers[i] = centers[i].replace("\"", "").replace("\'", "");
        _scales = new double[centers.length];
        for (int i = 0; i < centers.length; ++i) _scales[i] = Double.valueOf(centers[i]);
      }
    }
  }

  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    for (int i = 0; i < fr.numCols(); ++i) if (fr.vecs()[i].isEnum()) throw new IllegalArgumentException(("All columns must be numeric."));
    if (!(_centers == null) && _centers.length != fr.numCols()) throw new IllegalArgumentException("`centers` must be logical or have length equal to the number of columns in the dataset.");
    if (!(_scales  == null) && _scales.length  != fr.numCols()) throw new IllegalArgumentException("`scales` must be logical or have length equal to the number of columns in the dataset.");
    final boolean use_mean = _centers == null && _center;
    final double[] centers = _centers;
    final boolean use_sig  = _scales == null && _scale;
    final boolean use_rms  = !use_mean && _scale;
    final double[] scales  = _scales;
    if (!_center && !_scale && (_centers == null) && (_scales == null)) {
      //nothing to do, return the frame as is
      env.push0Ary(fr);
      return;
    }

    boolean doCenter = use_mean || _centers != null;
    boolean doScale  = use_sig || use_rms || _scales != null;

    Frame centered = new Frame(fr);
    if (doCenter) {
      centered = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          int rows = cs[0]._len;
          int cols = cs.length;
          for (int r = 0; r < rows; ++r)
            for (int c = 0; c < cols; ++c) {
              double numer = cs[c].at0(r) - (use_mean
                      ? cs[c].vec().mean()
                      : centers == null ? 0 : centers[c]);
              ncs[c].addNum(numer);
            }
        }
      }.doAll(fr.numCols(), fr).outputFrame(fr.names(), fr.domains());
    }

    double[] rms_vals = null;
    if (use_rms) {
      rms_vals = new double[fr.numCols()];
      double nrows = fr.numRows();
      for (int i = 0; i < rms_vals.length; ++i) {
        Vec v = centered.vecs()[i];
        ASTVar.CovarTask t = new ASTVar.CovarTask(0,0).doAll(new Frame(v,v));
        rms_vals[i] = Math.sqrt(t._ss / (nrows - 1));
      }
    }
    final double[] rms = rms_vals;

    Frame scaled = new Frame(centered);
    if (doScale) {
      scaled = new MRTask() {
        @Override
        public void map(Chunk[] cs, NewChunk[] ncs) {
          int rows = cs[0]._len;
          int cols = cs.length;
          for (int r = 0; r < rows; ++r)
            for (int c = 0; c < cols; ++c) {
              double denom = cs[c].at0(r) / (use_rms
                      ? rms[c] : use_sig ? cs[c].vec().sigma()
                      : scales == null ? 1 : scales[c]);
              ncs[c].addNum(denom);
            }
        }
      }.doAll(centered.numCols(), centered).outputFrame(centered.names(), centered.domains());
    }
    env.cleanup(fr);
    if (doScale) env.cleanup(centered);
    env.push(new ValFrame(scaled));
  }
}
//
//// ----
//abstract class ASTTimeOp extends ASTOp {
//  static Type[] newsig() {
//    Type t1 = Type.dblary();
//    return new Type[]{t1,t1};
//  }
//  ASTTimeOp() { super(VARS1,newsig(),OPF_PREFIX,OPP_PREFIX,OPA_RIGHT); }
//  abstract long op( MutableDateTime dt );
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    // Single instance of MDT for the single call
//    if( !env.isAry() ) {        // Single point
//      double d = env.popDbl();
//      if( !Double.isNaN(d) ) d = op(new MutableDateTime((long)d));
//      env.poppush(d);
//      return;
//    }
//    // Whole column call
//    Frame fr = env.popAry();
//    String skey = env.key();
//    final ASTTimeOp uni = this;  // Final 'this' so can use in closure
//    Frame fr2 = new MRTask2() {
//      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
//        MutableDateTime dt = new MutableDateTime(0);
//        for( int i=0; i<nchks.length; i++ ) {
//          NewChunk n =nchks[i];
//          Chunk c = chks[i];
//          int rlen = c._len;
//          for( int r=0; r<rlen; r++ ) {
//            double d = c.at0(r);
//            if( !Double.isNaN(d) ) {
//              dt.setMillis((long)d);
//              d = uni.op(dt);
//            }
//            n.addNum(d);
//          }
//        }
//      }
//    }.doAll(fr.numCols(),fr).outputFrame(fr._names, null);
//    env.subRef(fr,skey);
//    env.pop();                  // Pop self
//    env.push(fr2);
//  }
//}
//
//class ASTYear  extends ASTTimeOp { @Override String opStr(){ return "year" ; } @Override ASTOp make() {return new ASTYear  ();} @Override long op(MutableDateTime dt) { return dt.getYear();}}
//class ASTMonth extends ASTTimeOp { @Override String opStr(){ return "month"; } @Override ASTOp make() {return new ASTMonth ();} @Override long op(MutableDateTime dt) { return dt.getMonthOfYear()-1;}}
//class ASTDay   extends ASTTimeOp { @Override String opStr(){ return "day"  ; } @Override ASTOp make() {return new ASTDay   ();} @Override long op(MutableDateTime dt) { return dt.getDayOfMonth();}}
//class ASTHour  extends ASTTimeOp { @Override String opStr(){ return "hour" ; } @Override ASTOp make() {return new ASTHour  ();} @Override long op(MutableDateTime dt) { return dt.getHourOfDay();}}
//class ASTMinute extends ASTTimeOp { @Override String opStr(){return "minute";} @Override ASTOp make() {return new ASTMinute();} @Override long op(MutableDateTime dt) { return dt.getMinuteOfHour();}}
//class ASTSecond extends ASTTimeOp { @Override String opStr(){return "second";} @Override ASTOp make() {return new ASTSecond();} @Override long op(MutableDateTime dt) { return dt.getSecondOfMinute();}}
//class ASTMillis extends ASTTimeOp { @Override String opStr(){return "millis";} @Override ASTOp make() {return new ASTMillis();} @Override long op(MutableDateTime dt) { return dt.getMillisOfSecond();}}
//
//// Finite backward difference for user-specified lag
//// http://en.wikipedia.org/wiki/Finite_difference
//class ASTDiff extends ASTOp {
//  ASTDiff() { super(new String[]{"diff", "x", "lag", "differences"},
//          new Type[]{Type.ARY, Type.ARY, Type.DBL, Type.DBL},
//          OPF_PREFIX,
//          OPP_PREFIX,
//          OPA_RIGHT); }
//  @Override String opStr() { return "diff"; }
//  @Override ASTOp make() {return new ASTDiff();}
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    final int diffs = (int)env.popDbl();
//    if(diffs < 0) throw new IllegalArgumentException("differences must be an integer >= 1");
//    final int lag = (int)env.popDbl();
//    if(lag < 0) throw new IllegalArgumentException("lag must be an integer >= 1");
//
//    Frame fr = env.popAry();
//    String skey = env.key();
//    if(fr.vecs().length != 1 || fr.vecs()[0].isEnum())
//      throw new IllegalArgumentException("diff takes a single numeric column vector");
//
//    Frame fr2 = new MRTask2() {
//      @Override public void map(Chunk chk, NewChunk nchk) {
//        int rstart = (int)(diffs*lag - chk._start);
//        if(rstart > chk._len) return;
//        rstart = Math.max(0, rstart);
//
//        // Formula: \Delta_h^n x_t = \sum_{i=0}^n (-1)^i*\binom{n}{k}*x_{t-i*h}
//        for(int r = rstart; r < chk._len; r++) {
//          double x = chk.at0(r);
//          long row = chk._start + r;
//
//          for(int i = 1; i <= diffs; i++) {
//            double x_lag = chk.at_slow(row - i*lag);
//            double coef = ArithmeticUtils.binomialCoefficient(diffs, i);
//            x += (i % 2 == 0) ? coef*x_lag : -coef*x_lag;
//          }
//          nchk.addNum(x);
//        }
//      }
//    }.doAll(1,fr).outputFrame(fr.names(), fr.domains());
//    env.subRef(fr, skey);
//    env.pop();
//    env.push(fr2);
//  }
//}


/**
 *  ASTBinOp: E x E -&gt; E
 *
 *  This covers the class of operations that produce an array, scalar, or string from the cartesian product
 *  of the set E = {x | x is a string, scalar, or array}.
 */
abstract class ASTBinOp extends ASTOp {

  ASTBinOp() { super(VARS2); } // binary ops are infix ops

  ASTBinOp parse_impl(Exec E) {
    AST l = E.parse();
    AST r = E.skipWS().parse();
    ASTBinOp res = (ASTBinOp) clone();
    res._asts = new AST[]{l,r};
    return res;
  }

  abstract double op( double d0, double d1 );
  abstract String op( String s0, double d1 );
  abstract String op( double d0, String s1 );
  abstract String op( String s0, String s1 );

  @Override void apply(Env env) {
    // Expect we can broadcast across all functions as needed.
    Frame fr0 = null, fr1 = null;
    double d0=0, d1=0;
    String s0=null, s1=null;

    // Must pop ONLY twice off the stack
    int left_type = env.peekType();
    Object left = env.peek();
    int right_type = env.peekTypeAt(-1);
    Object right = env.peekAt(-1);

    // Cast the LHS of the op
    switch(left_type) {
      case Env.NUM: d0  = ((ValNum)left)._d; break;
      case Env.ARY: fr0 = ((ValFrame)left)._fr; break;
      case Env.STR: s0  = ((ValStr)left)._s; break;
      default: throw H2O.fail("Got unusable type: "+ left_type +" in binary operator "+ opStr());
    }

    // Cast the RHS of the op
    switch(right_type) {
      case Env.NUM: d1  = ((ValNum)right)._d; break;
      case Env.ARY: fr1 = ((ValFrame)right)._fr; break;
      case Env.STR: s1  = ((ValStr)right)._s; break;
      default: throw H2O.fail("Got unusable type: "+ right_type +" in binary operator "+ opStr());
    }

    // If both are doubles on the stack
    if( (fr0==null && fr1==null) && (s0==null && s1==null) ) { env.pop(); env.pop(); env.push(new ValNum(op(d0, d1))); return; }

    // One or both of the items on top of stack are Strings and neither are frames
    if( fr0==null && fr1==null) {
      env.pop(); env.pop();
      // s0 == null -> op(d0, s1)
      if (s0 == null) {
        // cast result of op if doing comparison, else combine the Strings if defined for op
        if (opStr().equals("==") || opStr().equals("!=")) env.push(new ValNum(Double.valueOf(op(d0,s1))));
        else env.push(new ValStr(op(d0,s1)));
      }
      // s1 == null -> op(s0, d1)
      else if (s1 == null) {
        // cast result of op if doing comparison, else combine the Strings if defined for op
        if (opStr().equals("==") || opStr().equals("!=")) env.push(new ValNum(Double.valueOf(op(s0,d1))));
        else env.push(new ValStr(op(s0,d1)));
      // s0 != null, s1 != null
      } else env.push(new ValStr(op(s0,s1)));
      return;
    }

    final boolean lf = fr0 != null;
    final boolean rf = fr1 != null;
    final double df0 = d0, df1 = d1;
    final String sf0 = s0, sf1 = s1;
    Frame fr;           // Do-All frame
    int ncols = 0;      // Result column count
    if( fr0 !=null ) {  // Left?
      ncols = fr0.numCols();
      if( fr1 != null ) {
        if( fr0.numCols() != fr1.numCols() ||
            fr0.numRows() != fr1.numRows() )
          throw new IllegalArgumentException("Arrays must be same size: LHS FRAME NUM ROWS/COLS: "+fr0.numRows()+"/"+fr0.numCols() +" vs RHS FRAME NUM ROWS/COLS: "+fr1.numRows()+"/"+fr1.numCols());
        fr = new Frame(fr0).add(fr1);
      } else {
        fr = new Frame(fr0);
      }
    } else {
      ncols = fr1.numCols();
      fr = new Frame(fr1);
    }
    final ASTBinOp bin = this;  // Final 'this' so can use in closure

    Key tmp_key = Key.make();
    // Run an arbitrary binary op on one or two frames & scalars
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        for( int i=0; i<nchks.length; i++ ) {
          NewChunk n =nchks[i];
          int rlen = chks[0]._len;
          Chunk c0 = chks[i];
          if( (!c0.vec().isEnum() &&
                  !(lf && rf && chks[i+nchks.length].vec().isEnum())) ||
                  bin instanceof ASTEQ ||
                  bin instanceof ASTNE ) {

            // Loop over rows
            for( int ro=0; ro<rlen; ro++ ) {
              double lv=0; double rv=0; String l=null; String r=null;

              // Initialize the lhs value
              if (lf) {
                if(chks[i].vec().isUUID() || (chks[i].isNA0(ro) && !bin.opStr().equals("|"))) { n.addNum(Double.NaN); continue; }
                if (chks[i].vec().isEnum()) l = chks[i].vec().domain()[(int)chks[i].at0(ro)];
                else lv = chks[i].at0(ro);
              } else if (sf0 == null) {
                if (Double.isNaN(df0) && !bin.opStr().equals("|")) { n.addNum(Double.NaN); continue; }
                lv = df0; l = null;
              } else {
                l = sf0;
              }

              // Initialize the rhs value
              if (rf) {
                if(chks[i+(lf ? nchks.length:0)].vec().isUUID() || chks[i].isNA0(ro) && !bin.opStr().equals("|")) { n.addNum(Double.NaN); continue; }
                if (chks[i].vec().isEnum()) r = chks[i].vec().domain()[(int)chks[i].at0(ro)];
                else rv = chks[i+(lf ? nchks.length:0)].at0(ro);
              } else if (sf1 == null) {
                if (Double.isNaN(df1) && !bin.opStr().equals("|")) { n.addNum(Double.NaN); continue; }
                rv = df1; r= null;
              } else {
                r = sf1;
              }

              // Append the value to the chunk after applying op(lhs,rhs)
              if (l == null && r == null)
                n.addNum(bin.op(lv, rv));
              else if (l == null) n.addNum(Double.valueOf(bin.op(lv,r)));
              else if (r == null) n.addNum(Double.valueOf(bin.op(l,rv)));
              else n.addNum(Double.valueOf(bin.op(l,r)));
            }
          } else {
            for( int r=0; r<rlen; r++ )  n.addNA();
          }
        }
      }
    }.doAll(ncols,fr).outputFrame(tmp_key, (lf ? fr0 : fr1)._names,null);
    if (env.isAry()) env.cleanup(env.popAry()); else env.pop();
    if (env.isAry()) env.cleanup(env.popAry()); else env.pop();
    env.push(new ValFrame(fr2));
  }
  @Override public String toString() { return "("+opStr()+" "+Arrays.toString(_asts)+")"; }
}

class ASTNot  extends ASTUniPrefixOp { public ASTNot()  { super(); } @Override String opStr(){ return "!";} @Override ASTOp make() {return new ASTNot(); } @Override double op(double d) { if (Double.isNaN(d)) return Double.NaN; return d==0?1:0; } }
class ASTPlus extends ASTBinOp { public ASTPlus() { super(); } @Override String opStr(){ return "+";} @Override ASTOp make() {return new ASTPlus();}
  @Override double op(double d0, double d1) { return d0+d1;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot add Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot add Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot add Strings.");}
}
class ASTSub extends ASTBinOp { public ASTSub() { super(); } @Override String opStr(){ return "-";} @Override ASTOp make() {return new ASTSub ();}
  @Override double op(double d0, double d1) { return d0-d1;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot subtract Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot subtract Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot subtract Strings.");}
}
class ASTMul extends ASTBinOp { public ASTMul() { super(); } @Override String opStr(){ return "*";} @Override ASTOp make() {return new ASTMul ();}
  @Override double op(double d0, double d1) { return d0*d1;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot multiply Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot multiply Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot multiply Strings.");}
}
class ASTDiv extends ASTBinOp { public ASTDiv() { super(); } @Override String opStr(){ return "/";} @Override ASTOp make() {return new ASTDiv ();}
  @Override double op(double d0, double d1) { return d0/d1;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot divide Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot divide Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot divide Strings.");}
}
class ASTPow extends ASTBinOp { public ASTPow() { super(); } @Override String opStr(){ return "^"  ;} @Override ASTOp make() {return new ASTPow ();}
  @Override double op(double d0, double d1) { return Math.pow(d0,d1);}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
}
class ASTPow2 extends ASTBinOp { public ASTPow2() { super(); } @Override String opStr(){ return "**" ;} @Override ASTOp make() {return new ASTPow2();}
  @Override double op(double d0, double d1) { return Math.pow(d0,d1);}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
}
class ASTMod extends ASTBinOp { public ASTMod() { super(); } @Override String opStr(){ return "%"  ;} @Override ASTOp make() {return new ASTMod ();}
  @Override double op(double d0, double d1) { return d0%d1;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot mod (%) Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot exponentiate Strings.");}
}
class ASTLT extends ASTBinOp { public ASTLT() { super(); } @Override String opStr(){ return "<"  ;} @Override ASTOp make() {return new ASTLT  ();}
  @Override double op(double d0, double d1) { return d0<d1 && !MathUtils.equalsWithinOneSmallUlp(d0,d1)?1:0;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot apply '<' to Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot apply '<' to Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot apply '<' to Strings.");}
}
class ASTLE extends ASTBinOp { public ASTLE() { super(); } @Override String opStr(){ return "<=" ;} @Override ASTOp make() {return new ASTLE  ();}
  @Override double op(double d0, double d1) { return d0<d1 ||  MathUtils.equalsWithinOneSmallUlp(d0,d1)?1:0;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot apply '<=' to Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot apply '<=' to Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot apply '<=' to Strings.");}
}
class ASTGT extends ASTBinOp { public ASTGT() { super(); } @Override String opStr(){ return ">"  ;} @Override ASTOp make() {return new ASTGT  ();}
  @Override double op(double d0, double d1) { return d0>d1 && !MathUtils.equalsWithinOneSmallUlp(d0,d1)?1:0;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot apply '>' to Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot apply '>' to Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot apply '>' to Strings.");}
}
class ASTGE extends ASTBinOp { public ASTGE() { super(); } @Override String opStr(){ return ">=" ;} @Override ASTOp make() {return new ASTGE  ();}
  @Override double op(double d0, double d1) { return d0>d1 ||  MathUtils.equalsWithinOneSmallUlp(d0,d1)?1:0;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot apply '>=' to Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot apply '>=' to Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot apply '>=' to Strings.");}
}
class ASTEQ extends ASTBinOp { public ASTEQ() { super(); } @Override String opStr(){ return "==" ;} @Override ASTOp make() {return new ASTEQ  ();}
  @Override double op(double d0, double d1) { return MathUtils.equalsWithinOneSmallUlp(d0,d1)?1:0;}
  @Override String op(String s0, double d1) { return s0.equals(Double.toString(d1)) ? "1.0" : "0.0"; }
  @Override String op(double d0, String s1) { return (Double.toString(d0)).equals(s1) ? "1.0" : "0.0";}
  @Override String op(String s0, String s1) { return s0.equals(s1) ? "1.0" : "0.0"; }
}
class ASTNE extends ASTBinOp { public ASTNE() { super(); } @Override String opStr(){ return "!=" ;} @Override ASTOp make() {return new ASTNE  ();}
  @Override double op(double d0, double d1) { return MathUtils.equalsWithinOneSmallUlp(d0,d1)?0:1;}
  @Override String op(String s0, double d1) { return !s0.equals(Double.toString(d1)) ? "1.0" : "0.0"; }
  @Override String op(double d0, String s1) { return !(Double.toString(d0)).equals(s1) ? "1.0" : "0.0";}
  @Override String op(String s0, String s1) { return !s0.equals(s1) ? "1.0" : "0.0"; }
}
class ASTLA extends ASTBinOp { public ASTLA() { super(); } @Override String opStr(){ return "&"  ;} @Override ASTOp make() {return new ASTLA  ();}
  @Override double op(double d0, double d1) { return (d0!=0 && d1!=0) ? (Double.isNaN(d0) || Double.isNaN(d1)?Double.NaN:1) :0;}
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot '&' Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot '&' Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot '&' Strings.");}
}
class ASTLO extends ASTBinOp { public ASTLO() { super(); } @Override String opStr(){ return "|"  ;} @Override ASTOp make() {return new ASTLO  ();}
  @Override double op(double d0, double d1) {
    if (d0 == 0 && Double.isNaN(d1)) { return Double.NaN; }
    if (d1 == 0 && Double.isNaN(d0)) { return Double.NaN; }
    if (Double.isNaN(d0) && Double.isNaN(d1)) { return Double.NaN; }
    if (d0 == 0 && d1 == 0) { return 0; }
    return 1;
  }
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot '|' Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot '|' Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot '|' Strings.");}
}

// Variable length; instances will be created of required length
abstract class ASTReducerOp extends ASTOp {
  final double _init;
  protected static boolean _narm;        // na.rm in R
  protected static int _argcnt;
  ASTReducerOp( double init) {
    super(new String[]{"","dblary","...", "na.rm"});
    _init = init;
  }

  ASTReducerOp parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST ary = E.parse();
    dblarys.add(ary);
    AST a;
    E.skipWS();
    while (true) {
      a = E.skipWS().parse();
      if (a instanceof ASTId) {
        AST ast = E._env.lookup((ASTId)a);
        if (ast instanceof ASTFrame) {dblarys.add(a); continue; } else break;
      }
      if (a instanceof ASTNum || a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTBinOp || a instanceof ASTUniOp || a instanceof ASTReducerOp)
        dblarys.add(a);
      else break;
    }
    // Get the na.rm last
    a = E._env.lookup((ASTId)a);
    _narm = ((ASTNum)a).dbl() == 1;
    ASTReducerOp res = (ASTReducerOp) clone();
    AST[] arys = new AST[_argcnt = dblarys.size()];
    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
    res._asts = arys;
    return res;
  }

  @Override double[] map(Env env, double[] in, double[] out) {
    double s = _init;
    for (double v : in) if (!_narm || !Double.isNaN(v)) s = op(s,v);
    if (out == null || out.length < 1) out = new double[1];
    out[0] = s;
    return out;
  }
  abstract double op( double d0, double d1 );
  @Override void apply(Env env) {
    double sum=_init;
    int argcnt = _argcnt;
    for( int i=0; i<argcnt; i++ )
      if( env.isNum() ) sum = op(sum,env.popDbl());
      else {
        Frame fr = env.pop0Ary(); // pop w/o lowering refcnts ... clean it up later
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        sum = op(sum,_narm?new NaRmRedOp(this).doAll(fr)._d:new RedOp(this).doAll(fr)._d);
        env.cleanup(fr);
      }
    env.push(new ValNum(sum));
  }

  private static class RedOp extends MRTask<RedOp> {
    final ASTReducerOp _bin;
    RedOp( ASTReducerOp bin ) { _bin = bin; _d = bin._init; }
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        if (C.vec().isEnum() || C.vec().isUUID() || C.vec().isString()) continue; // skip enum/uuid vecs
        for (int r = 0; r < rows; r++)
          _d = _bin.op(_d, C.at0(r));
        if (Double.isNaN(_d)) break;
      }
    }
    @Override public void reduce( RedOp s ) { _d = _bin.op(_d,s._d); }
  }

  private static class NaRmRedOp extends MRTask<NaRmRedOp> {
    final ASTReducerOp _bin;
    NaRmRedOp( ASTReducerOp bin ) { _bin = bin; _d = bin._init; }
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        if (C.vec().isEnum() || C.vec().isUUID() || C.vec().isString()) continue; // skip enum/uuid vecs
        for (int r = 0; r < rows; r++)
          if (!Double.isNaN(C.at0(r)))
            _d = _bin.op(_d, C.at0(r));
        if (Double.isNaN(_d)) break;
      }
    }
    @Override public void reduce( NaRmRedOp s ) { _d = _bin.op(_d,s._d); }
  }
}

class ASTSum extends ASTReducerOp { ASTSum() {super(0);} @Override String opStr(){ return "sum";} @Override ASTOp make() {return new ASTSum();} @Override double op(double d0, double d1) { return d0+d1;}}

//class ASTReduce extends ASTOp {
//  static final String VARS[] = new String[]{ "", "op2", "ary"};
//  static final Type   TYPES[]= new Type  []{ Type.ARY, Type.fcn(new Type[]{Type.DBL,Type.DBL,Type.DBL}), Type.ARY };
//  ASTReduce( ) { super(VARS,TYPES,OPF_PREFIX,OPP_PREFIX,OPA_RIGHT); }
//  @Override String opStr(){ return "Reduce";}
//  @Override ASTOp make() {return this;}
//  @Override void apply(Env env, int argcnt, ASTApply apply) { throw H2O.unimpl(); }
//}

// Check that this properly cleans up all frames.
class ASTCbind extends ASTUniPrefixOp {
  protected static int argcnt;
  @Override String opStr() { return "cbind"; }
  ASTCbind( ) { super(new String[]{"cbind","ary", "..."}); }
  @Override ASTOp make() {return this;}
  ASTCbind parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST ary = E.parse();
    dblarys.add(ary);
    AST a = null;
    while (E.skipWS().hasNext()) {
      a = E.parse();
      if (a instanceof ASTId) {
        AST ast = E._env.lookup((ASTId)a);
        if (ast instanceof ASTFrame) {dblarys.add(a); continue; }
      }
      if (a instanceof ASTNum || a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTBinOp || a instanceof ASTUniOp || a instanceof ASTReducerOp)
        dblarys.add(a);
    }
    ASTCbind res = (ASTCbind) clone();
    AST[] arys = new AST[argcnt=dblarys.size()];
    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
    res._asts = arys;
    return res;
  }
  @Override void apply(Env env) {
    //argcnt = env.sp();
    // Validate the input frames
    Vec vmax = null;
    for(int i = 0; i < argcnt; i++) {
      Frame t = env.peekAryAt(-i);
      if(vmax == null) vmax = t.vecs()[0];
      else if(t.numRows() != vmax.length())
        // R pads shorter cols to match max rows by cycling/repeating, but we won't support that
        throw new IllegalArgumentException("Row mismatch! Expected " + String.valueOf(vmax.length()) + " but frame has " + String.valueOf(t.numRows()));
    }

    // loop over frames and combine
    Frame fr = new Frame(new String[0],new Vec[0]);
    for(int i = 0; i < argcnt; i++) {
      Frame f = env.pop0Ary();
      Frame new_frame = fr.makeCompatible(f);
      if (f.numCols() == 1) fr.add(f.names()[0], new_frame.anyVec());
      else fr.add(new_frame);
    }

    env.push(new ValFrame(fr));
  }
}

class ASTMin extends ASTReducerOp {
  ASTMin( ) { super( Double.POSITIVE_INFINITY); }
  @Override String opStr(){ return "min";}
  @Override ASTOp make() {return new ASTMin();}
  @Override double op(double d0, double d1) { return Math.min(d0, d1); }
  ASTMin parse_impl(Exec E) { return (ASTMin)super.parse_impl(E); }
  @Override void apply(Env env) {
    double min = Double.POSITIVE_INFINITY;
    int argcnt = env.sp();
    for( int i=0; i<argcnt; i++ )
      if( env.isNum() ) min = Math.min(min, env.popDbl());
      else {
        Frame fr = env.pop0Ary();
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        for (Vec v : fr.vecs())
          if (v.naCnt() > 0 && !_narm) { min = Double.NaN; break; }
          else min = Math.min(min, v.min());
        env.cleanup(fr);
      }
    env.push(new ValNum(min));
  }
}

class ASTMax extends ASTReducerOp {
  ASTMax( ) { super( Double.NEGATIVE_INFINITY); }
  @Override String opStr(){ return "max";}
  @Override ASTOp make() {return new ASTMax();}
  @Override double op(double d0, double d1) { return Math.max(d0,d1); }
  ASTMax parse_impl(Exec E) { return (ASTMax)super.parse_impl(E); }
  @Override void apply(Env env) {
    double max = Double.NEGATIVE_INFINITY;
    int argcnt = env.sp();
    for( int i=0; i<argcnt; i++ )
      if( env.isNum() ) max = Math.max(max, env.popDbl());
      else {
        Frame fr = env.pop0Ary();
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        for (Vec v : fr.vecs())
          if (v.naCnt() > 0 && !_narm) { max = Double.NaN; break; }
          else max = Math.max(max, v.max());
        env.cleanup(fr);
      }
    env.push(new ValNum(max));
  }
}

// R like binary operator &&
class ASTAND extends ASTBinOp {
  @Override String opStr() { return "&&"; }
  ASTAND( ) {super();}
  @Override double op(double d0, double d1) { throw H2O.fail(); }
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot '&&' Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot '&&' Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot '&&' Strings.");}

  @Override ASTOp make() { return new ASTAND(); }
  @Override void apply(Env env) {
    double op1 = (env.isNum()) ? env.peekDbl()
            : (env.isAry() ? env.peekAry().vecs()[0].at(0) : Double.NaN);
    env.pop();
    double op2 = (env.isNum()) ? env.peekDbl()
            : (env.isAry() ? env.peekAry().vecs()[0].at(0) : Double.NaN);
    env.pop();

    // Both NAN ? push NaN
    if (Double.isNaN(op1) && Double.isNaN(op2)) {
      env.push(new ValNum(Double.NaN));
      return;
    }

    // Either 0 ? push False
    if (op1 == 0 || op2 == 0) {
      env.push(new ValNum(0.0));
      return;
    }

    // Either NA ? push NA (no need to worry about 0s, taken care of in case above)
    if (Double.isNaN(op1) || Double.isNaN(op2)) {
      env.push(new ValNum(Double.NaN));
      return;
    }

    // Otherwise, push True
    env.push(new ValNum(1.0));
  }
}

class ASTRename extends ASTUniPrefixOp {
  protected String _newname;
  @Override String opStr() { return "rename"; }
  ASTRename() { super(new String[] {"", "ary", "new_name"}); }
  @Override ASTOp make() { return new ASTRename(); }
  ASTRename parse_impl(Exec E) {
    AST ary = E.parse();
    _newname = ((ASTString)E.skipWS().parse())._s;
    ASTRename res = (ASTRename) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Frame fr = e.pop0Ary();
    Futures fs = new Futures();
    Frame ff = DKV.remove(fr._key, fs).get();
    fs.blockForPending();
    Frame fr2 = new Frame(Key.make(_newname), ff.names(), ff.vecs());
    Futures fs2 = new Futures();
    DKV.put(Key.make(_newname), fr2, fs2);
    fs2.blockForPending();
    e.push0(new ValFrame(fr2));  // the vecs have not changed and their refcnts remain the same
  }
}

class ASTMatch extends ASTUniPrefixOp {
  double _nomatch;
  String[] _matches;
  @Override String opStr() { return "match"; }
  ASTMatch() { super( new String[]{"", "ary", "table", "nomatch", "incomparables"}); }
  @Override ASTOp make() { return new ASTMatch(); }
  ASTMatch parse_impl(Exec E) {
    // First parse out the `ary` arg
    AST ary = E.parse();
    // The `table` arg
    _matches = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : new String[]{E.parseString(E.peekPlus())};
    // cleanup _matches
    for (int i = 0; i < _matches.length; ++i) _matches[i] = _matches[i].replace("\"", "").replace("\'", "");
    // `nomatch` is just a number in case no match
    ASTNum nomatch = (ASTNum)E.skipWS().parse(); _nomatch = nomatch.dbl();
    // drop the incomparables arg for now ...
    AST incomp = E.skipWS().parse();
    ASTMatch res = (ASTMatch) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Frame fr = e.pop0Ary();
    if (fr.numCols() != 1) throw new IllegalArgumentException("can only match on a single categorical column.");
    if (!fr.anyVec().isEnum()) throw new IllegalArgumentException("can only match on a single categorical column.");
    Key tmp = Key.make();
    final String[] matches = _matches;
    Frame rez = new MRTask() {
      private int in(String s) { return Arrays.asList(matches).contains(s) ? 1 : 0; }
      @Override public void map(Chunk c, NewChunk n) {
        int rows = c._len;
        for (int r = 0; r < rows; ++r) n.addNum(in(c.vec().domain()[(int)c.at80(r)]));
      }
    }.doAll(1, fr.anyVec()).outputFrame(tmp, null, null);
    e.cleanup(fr);
    e.push(new ValFrame(rez));
  }

}

// R like binary operator ||
class ASTOR extends ASTBinOp {
  @Override String opStr() { return "||"; }
  ASTOR( ) { super(); }
  @Override double op(double d0, double d1) { throw H2O.fail(); }
  @Override String op(String s0, double d1) {throw new IllegalArgumentException("Cannot '||' Strings.");}
  @Override String op(double d0, String s1) {throw new IllegalArgumentException("Cannot '||' Strings.");}
  @Override String op(String s0, String s1) {throw new IllegalArgumentException("Cannot '||' Strings.");}

  @Override ASTOp make() { return new ASTOR(); }
  @Override void apply(Env env) {
    double op1 = (env.isNum()) ? env.peekDbl()
            : (env.isAry() ? env.peekAry().vecs()[0].at(0) : Double.NaN);
    env.pop();
    // op1 is NaN ? push NaN
    if (Double.isNaN(op1)) {
      env.pop();
      env.push(new ValNum(Double.NaN));
      return;
    }
    double op2 = !Double.isNaN(op1) && op1!=0 ? 1 : (env.isNum()) ? env.peekDbl()
                    : (env.isAry()) ? env.peekAry().vecs()[0].at(0) : Double.NaN;
    env.pop();

    // op2 is NaN ? push NaN
    if (Double.isNaN(op2)) {
      env.push(new ValNum(op2));
      return;
    }

    // both 0 ? push False
    if (op1 == 0 && op2 == 0) {
      env.push(new ValNum(0.0));
      return;
    }

    // else push True
    env.push(new ValNum(1.0));
  }
}

// Similar to R's seq_len
class ASTSeqLen extends ASTUniPrefixOp {
  double _length;
  @Override String opStr() { return "seq_len"; }
  ASTSeqLen( ) { super(new String[]{"seq_len", "n"}); }
  @Override ASTOp make() { return this; }
  @Override ASTSeqLen parse_impl(Exec E) {
    _length = E.nextDbl();
    ASTSeqLen res = (ASTSeqLen) clone();
    res._asts = new AST[]{};
    return res;
  }

  @Override void apply(Env env) {
    int len = (int) Math.ceil(_length);
    if (len <= 0)
      throw new IllegalArgumentException("Error in seq_len(" +len+"): argument must be coercible to positive integer");
    Frame fr = new Frame(new String[]{"c"}, new Vec[]{Vec.makeSeq(len)});
    env.push(new ValFrame(fr));
  }
}

// Same logic as R's generic seq method
class ASTSeq extends ASTUniPrefixOp {
  double _from;
  double _to;
  double _by;

  @Override String opStr() { return "seq"; }
  ASTSeq() { super(new String[]{"seq", "from", "to", "by"}); }
  @Override ASTOp make() { return this; }
  @Override ASTSeq parse_impl(Exec E) {
    // *NOTE*: This function creates a frame, there is no input frame!
//    AST ary = E.parse();
    // Get the from
    _from = E.nextDbl();
    // Get the to
    _to = E.nextDbl();
    // Get the by
    _by = E.nextDbl();
    // Finish the rest
    ASTSeq res = (ASTSeq) clone();
    res._asts = new AST[]{}; // in reverse order so they appear correctly on the stack.
    return res;
  }

  @Override void apply(Env env) {
    double delta = _to - _from;
    if(delta == 0 && _to == 0)
      env.push(new ValNum(_to));
    else {
      double n = delta/_by;
      if(n < 0)
        throw new IllegalArgumentException("wrong sign in 'by' argument");
      else if(n > Double.MAX_VALUE)
        throw new IllegalArgumentException("'by' argument is much too small");

      double dd = Math.abs(delta)/Math.max(Math.abs(_from), Math.abs(_to));
      if(dd < 100*Double.MIN_VALUE)
        env.push(new ValNum(_from));
      else {
        Futures fs = new Futures();
        AppendableVec av = new AppendableVec(Vec.newKey());
        NewChunk nc = new NewChunk(av, 0);
        int len = (int)n + 1;
        for (int r = 0; r < len; r++) nc.addNum(_from + r*_by);
        // May need to adjust values = by > 0 ? min(values, to) : max(values, to)
        nc.close(0, fs);
        Vec vec = av.close(fs);
        fs.blockForPending();
        Frame fr = new Frame(new String[]{"C1"}, new Vec[]{vec});
        env.push(new ValFrame(fr));
      }
    }
  }
}

class ASTRepLen extends ASTUniPrefixOp {
  double _length;
  @Override String opStr() { return "rep_len"; }
  ASTRepLen() { super(new String[]{"rep_len", "x", "length.out"}); }
  @Override ASTOp make() { return this; }
  @Override void apply(Env env) {

    // two cases if x is a frame: x is a single vec, x is a list of vecs
    if (env.isAry()) {
      final Frame fr = env.pop0Ary();
      if (fr.numCols() == 1) {

        // In this case, create a new vec of length _length using the elements of x
        Vec v = Vec.makeRepSeq((long)_length, fr.numRows());  // vec of "indices" corresponding to rows in x
        new MRTask() {
          @Override public void map(Chunk c) {
            for (int i = 0; i < c._len; ++i)
              c.set0(i, fr.anyVec().at((long)c.at0(i)));
          }
        }.doAll(v);
        v.setDomain(fr.anyVec().domain());
        Frame f = new Frame(new String[]{"C1"}, new Vec[]{v});
        env.cleanup(fr);
        env.push(new ValFrame(f));

      } else {

        // In this case, create a new frame with numCols = _length using the vecs of fr
        // this is equivalent to what R does, but by additionally calling "as.data.frame"
        String[] col_names = new String[(int)_length];
        for (int i = 0; i < col_names.length; ++i) col_names[i] = "C" + (i+1);
        Frame f = new Frame(col_names, new Vec[(int)_length]);
        for (int i = 0; i < f.numCols(); ++i)
          f.add(Frame.defaultColName(f.numCols()), fr.vec( i % fr.numCols() ));

        env.cleanup(fr);
        env.push(new ValFrame(f));
      }
    }

    // x is a number or a string
    else {
      int len = (int)_length;
      if(len <= 0)
        throw new IllegalArgumentException("Error in rep_len: argument length.out must be coercible to a positive integer");
      if (env.isStr()) {
        // make a constant enum vec with domain[] = []{env.popStr()}
        Frame fr = new Frame(new String[]{"C1"}, new Vec[]{Vec.makeCon(0, len)});
        fr.anyVec().setDomain(new String[]{env.popStr()});
        env.push(new ValFrame(fr));
      } else if (env.isNum()) {
        Frame fr = new Frame(new String[]{"C1"}, new Vec[]{Vec.makeCon(env.popDbl(), len)});
        env.push(new ValFrame(fr));
      } else throw new IllegalArgumentException("Unkown input. Type: "+env.peekType() + " Stack: " + env.toString());
    }
  }
}

// Compute exact quantiles given a set of cutoffs, using multipass binning algo.
class ASTQtile extends ASTUniPrefixOp {
  protected static boolean _narm = false;
  protected static boolean _names= true;  // _names = true, create a  vec of names as %1, %2, ...; _names = false -> no vec.
  protected static int     _type = 7;
  protected static double[] _probs = null;  // if probs is null, pop the _probs frame etc.

  @Override String opStr() { return "quantile"; }

  ASTQtile( ) { super(new String[]{"quantile","x","probs", "na.rm", "names", "type"});}
  @Override ASTQtile make() { return new ASTQtile(); }
  @Override ASTQtile parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // parse the probs, either a ASTSeries or an ASTSeq -> resulting in a Frame _ONLY_
    AST seq = null;
    // if is ASTSeries:
    if (E.skipWS().peek() == '{') {
      String[] ps = E.xpeek('{').parseString('}').split(";");
      _probs = new double[ps.length];
      for (int i = 0; i < ps.length; ++i) {
        double v = Double.valueOf(ps[i]);
        if (v < 0 || v > 1) throw new  IllegalArgumentException("Quantile: probs must be in the range of [0, 1].");
        _probs[i] = v;
      }

    // else ASTSeq
    } else seq = E.parse();
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    _narm = ((ASTNum)a).dbl() == 1;
    // Get the names
    AST b = E._env.lookup((ASTId)E.skipWS().parse());
    _names = ((ASTNum)b).dbl() == 1;
    //Get the type
    _type = (int)((ASTNum)E.skipWS().parse()).dbl();
    // Finish the rest
    ASTQtile res = (ASTQtile) clone();
    res._asts = seq == null ? new AST[]{ary} : new AST[]{ary, seq}; // in reverse order so they appear correctly on the stack.
    return res;
  }


  @Override void apply(Env env) {
    final Frame probs = _probs == null ? env.pop0Ary() : null;
    if (probs != null && probs.numCols() != 1) throw new IllegalArgumentException("Probs must be a single vector.");

    Frame x = env.pop0Ary();
    if (x.numCols() != 1) throw new IllegalArgumentException("Must specify a single column in quantile. Got: "+ x.numCols() + " columns.");
    Vec xv  = x.anyVec();
    if ( xv.isEnum() ) {
      throw new  IllegalArgumentException("Quantile: column type cannot be Enum.");
    }

    double p[];

    Vec pv = probs == null ? null : probs.anyVec();
    if (pv != null) {
      p = new double[(int)pv.length()];
      for (int i = 0; i < pv.length(); i++) {
        if ((p[i] = pv.at((long) i)) < 0 || p[i] > 1)
          throw new IllegalArgumentException("Quantile: probs must be in the range of [0, 1].");
      }
    } else p = _probs;

    String[] names = new String[p.length];
    for (int i = 0; i < names.length; ++i) names[i] = Double.toString(p[i]) + "%";

    // create output vec
    Vec res = Vec.makeZero(p.length);
    Vec p_names = Vec.makeSeq(res.length());
    p_names.setDomain(names);


    final int MAX_ITERATIONS = 16;
    final int MAX_QBINS = 1000; // less uses less memory, can take more passes
    final boolean MULTIPASS = true; // approx in 1 pass if false
    // Type 7 matches R default
    final int INTERPOLATION = _type; // 7 uses linear if quantile not exact on row. 2 uses mean.

    // a little obtuse because reusing first pass object, if p has multiple thresholds
    // since it's always the same (always had same valStart/End seed = vec min/max
    // some MULTIPASS conditionals needed if we were going to make this work for approx or exact
    final Quantiles[] qbins1 = new Quantiles.BinTask2(MAX_QBINS, xv.min(), xv.max()).doAll(xv)._qbins;
    for( int i=0; i<p.length; i++ ) {
      double quantile = p[i];
      // need to pass a different threshold now for each finishUp!
      qbins1[0].finishUp(xv, new double[]{quantile}, INTERPOLATION, MULTIPASS);
      if( qbins1[0]._done ) {
        res.set(i,qbins1[0]._pctile[0]);
      } else {
        // the 2-N map/reduces are here (with new start/ends. MULTIPASS is implied
        Quantiles[] qbinsM = new Quantiles.BinTask2(MAX_QBINS, qbins1[0]._newValStart, qbins1[0]._newValEnd).doAll(xv)._qbins;
        for( int iteration = 2; iteration <= MAX_ITERATIONS; iteration++ ) {
          qbinsM[0].finishUp(xv, new double[]{quantile}, INTERPOLATION, MULTIPASS);
          if( qbinsM[0]._done ) {
            res.set(i,qbinsM[0]._pctile[0]);
            break;
          }
          // the 2-N map/reduces are here (with new start/ends. MULTIPASS is implied
          qbinsM = new Quantiles.BinTask2(MAX_QBINS, qbinsM[0]._newValStart, qbinsM[0]._newValEnd).doAll(xv)._qbins;
        }
      }
    }
    res.chunkForChunkIdx(0).close(0,null);
    p_names.chunkForChunkIdx(0).close(0, null);
    Futures pf = p_names.postWrite(new Futures());
    pf.blockForPending();
    Futures f = res.postWrite(new Futures());
    f.blockForPending();
    Frame fr = new Frame(new String[]{"P", "Q"}, new Vec[]{p_names, res});
    env.cleanup(probs, x);
    env.push(new ValFrame(fr));
  }
}

class ASTRunif extends ASTUniPrefixOp {
  protected static double _min;
  protected static double _max;
  protected static long   _seed;
  @Override String opStr() { return "runif"; }
  ASTRunif() { super(new String[]{"runif","dbls","seed"}); }
  @Override ASTOp make() {return new ASTRunif();}
  @Override ASTRunif parse_impl(Exec E) {
    // peel off the ary
    AST ary = E.parse();
    // parse the min
    _min = E.nextDbl();
    // parse the max
    _max = E.nextDbl();
    // parse the seed
    _seed = (long)E.nextDbl();
    ASTRunif res = (ASTRunif) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env env) {
    final long seed = _seed == -1 ? (new Random().nextLong()) : _seed;
    Frame fr = env.pop0Ary();
    Vec randVec = fr.anyVec().makeZero();
    new MRTask() {
      @Override public void map(Chunk c){
        Random rng = new Random(seed*c.cidx());
        for(int i = 0; i < c._len; ++i)
          c.set0(i, (float)rng.nextDouble());
      }
    }.doAll(randVec);
    Frame f = new Frame(new String[]{"rnd"}, new Vec[]{randVec});
    env.cleanup(fr);
    env.push(new ValFrame(f));
  }
}

class ASTSdev extends ASTUniPrefixOp {
  boolean _narm = false;
  public ASTSdev() { super(new String[]{"sd", "ary", "na.rm"}); }
  @Override String opStr() { return "sd"; }
  @Override ASTOp make() { return new ASTSdev(); }
  @Override ASTSdev parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    _narm = ((ASTNum)a).dbl() == 1;
    ASTSdev res = (ASTSdev) clone();
    res._asts = new AST[]{ary}; // in reverse order so they appear correctly on the stack.
    return res;
  }
  @Override void apply(Env env) {
    Frame fr = env.peekAry();
    if (fr.vecs().length > 1)
      throw new IllegalArgumentException("sd does not apply to multiple cols.");
    if (fr.vecs()[0].isEnum())
      throw new IllegalArgumentException("sd only applies to numeric vector.");

    double sig = Math.sqrt(ASTVar.getVar(fr.anyVec(), _narm));
    if (env.isAry()) env.cleanup(env.popAry()); else env.pop();
    env.push(new ValNum(sig));
  }
}

class ASTVar extends ASTUniPrefixOp {
  boolean _narm = false;
  boolean _ynull = false;
  public ASTVar() { super(new String[]{"var", "ary", "y", "na.rm", "use"}); } // the order Vals appear on the stack
  @Override String opStr() { return "var"; }
  @Override ASTOp make() { return new ASTVar(); }
  @Override ASTVar parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // Get the trim
    AST y = E.skipWS().parse();
    if (y instanceof ASTString && ((ASTString)y)._s.equals("null")) {_ynull = true; y = ary; }
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    _narm = ((ASTNum)a).dbl() == 1;
    // Get the `use`
    ASTString use = (ASTString) E.skipWS().parse();
    // Finish the rest
    ASTVar res = (ASTVar) clone();
    res._asts = new AST[]{use,y,ary}; // in reverse order so they appear correctly on the stack.
    return res;
  }

  @Override void apply(Env env) {
    if (env.isNum()) {
      env.pop();
      env.push(new ValNum(Double.NaN));
    } else {
      Frame fr = env.peekAry();                   // number of rows
      Frame y = ((ValFrame) env.peekAt(-1))._fr;  // number of columns
      String use = ((ValStr) env.peekAt(-2))._s;  // what to do w/ NAs: "everything","all.obs","complete.obs","na.or.complete","pairwise.complete.obs"
//      String[] rownames = fr.names();  TODO: Propagate rownames?
      String[] colnames = y.names();

      if (fr.numRows() != y.numRows())
        throw new IllegalArgumentException("In var(): incompatible dimensions. Frames must have the same number of rows.");

      if (use.equals("everything")) _narm = false;
      if (use.equals("complete.obs")) _narm = true;
      if (use.equals("all.obs")) _narm = false;
      final double[/*cols*/][/*rows*/] covars = new double[y.numCols()][fr.numCols()];
      final CovarTask tsks[][] = new CovarTask[y.numCols()][fr.numCols()];
      final Frame frs[][] = new Frame[y.numCols()][fr.numCols()];
      final double xmeans[] = new double[fr.numCols()];
      final double ymeans[] = new double[y.numCols()];
      for (int r = 0; r < fr.numCols(); ++r) xmeans[r] = getMean(fr.vecs()[r], _narm, use);
      for (int c = 0; c < y.numCols(); ++c) ymeans[c]  = getMean( y.vecs()[c], _narm, use);
      for (int c = 0; c < y.numCols(); ++c) {
        for (int r = 0; r < fr.numCols(); ++r) {
          frs[c][r] = new Frame(y.vecs()[c], fr.vecs()[r]);
          tsks[c][r] = new CovarTask(ymeans[c], xmeans[r]).dfork(frs[c][r]);
        }
      }
      for (int c = 0; c < y.numCols(); c++)
        for (int r = 0; r < fr.numCols(); r++) {
          covars[c][r] = tsks[c][r].getResult()._ss / (fr.numRows() - 1);
          env.remove(frs[c][r], true); //cleanup
          frs[c][r] = null;
        }

      if (env.isAry()) env.cleanup(env.popAry()); else env.pop();  // pop fr
      if (env.isAry()) env.cleanup(env.popAry()); else  env.pop(); // pop y
      env.pop(); // pop use

      // Just push the scalar if input is a single col
      if (covars.length == 1 && covars[0].length == 1) env.push(new ValNum(covars[0][0]));
      else {
        // Build output vecs for var-cov matrix
        Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(covars.length);
        Vec[] vecs = new Vec[covars.length];
        for (int i = 0; i < covars.length; i++) {
          AppendableVec v = new AppendableVec(keys[i]);
          NewChunk c = new NewChunk(v, 0);
          for (int j = 0; j < covars[0].length; j++) c.addNum(covars[i][j]);
          c.close(0, null);
          vecs[i] = v.close(null);
        }
        env.push(new ValFrame(new Frame(colnames, vecs)));
      }
    }
  }

  static double getMean(Vec v, boolean narm, String use) {
    ASTMean.MeanNARMTask t = new ASTMean.MeanNARMTask(narm).doAll(v);
    if (t._rowcnt == 0 || Double.isNaN(t._sum)) {
      if (use.equals("all.obs")) throw new IllegalArgumentException("use = \"all.obs\" with missing observations.");
      return Double.NaN;
    }
    return t._sum / t._rowcnt;
  }

  static double getVar(Vec v, boolean narm) {
    double m = getMean( v, narm, "");
    CovarTask t = new CovarTask(m,m).doAll(new Frame(v, v));
    return t._ss / (v.length() - 1);
  }

  static class CovarTask extends MRTask<CovarTask> {
    double _ss;
    double _xmean;
    double _ymean;
    CovarTask(double xmean, double ymean) { _xmean = xmean; _ymean = ymean; }
    @Override public void map(Chunk[] cs) {
      int len = cs[0]._len;
      Chunk x = cs[0];
      Chunk y = cs[1];
      if (Double.isNaN(_xmean) || Double.isNaN(_ymean)) { _ss = Double.NaN; return; }
      for (int r = 0; r < len; ++r) {
        _ss += (x.at0(r) - _xmean) * (y.at0(r) - _ymean);
      }
    }
    @Override public void reduce(CovarTask tsk) { _ss += tsk._ss; }
  }
}

class ASTMean extends ASTUniPrefixOp {
  double  _trim = 0;
  boolean _narm = false;
  public ASTMean() { super(new String[]{"mean", "ary", "trim", "na.rm"}); }
  @Override String opStr() { return "mean"; }
  @Override ASTOp make() { return new ASTMean(); }
  @Override ASTMean parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // Get the trim
    _trim = ((ASTNum)(E.skipWS().parse())).dbl();
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    _narm = ((ASTNum)a).dbl() == 1;
    // Finish the rest
    ASTMean res = (ASTMean) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env env) {
    Frame fr = env.peekAry(); // get the frame w/o popping/sub-reffing
    if (fr.vecs().length > 1)
      throw new IllegalArgumentException("mean does not apply to multiple cols.");
    if (fr.vecs()[0].isEnum())
      throw new IllegalArgumentException("mean only applies to numeric vector.");
    MeanNARMTask t = new MeanNARMTask(_narm).doAll(fr.anyVec()).getResult();
    if (t._rowcnt == 0 || Double.isNaN(t._sum)) {
      double ave = Double.NaN;
      if (env.isAry()) env.cleanup(env.popAry()); else env.pop();
      env.push(new ValNum(ave));
    } else {
      double ave = t._sum / t._rowcnt;
      if (env.isAry()) env.cleanup(env.popAry()); else env.pop();
      env.push(new ValNum(ave));
    }
  }

  // Keep this map for legacy reasons (in case H2O Console is rezzed).
  @Override double[] map(Env env, double[] in, double[] out) {
    if (out == null || out.length < 1) out = new double[1];
    double s = 0;  int cnt=0;
    for (double v : in) if( !Double.isNaN(v) ) { s+=v; cnt++; }
    out[0] = s/cnt;
    return out;
  }

  static class MeanNARMTask extends MRTask<MeanNARMTask> {
    // IN
    boolean _narm;    // remove NAs
    double  _trim;    // trim each end of the column -- unimplemented: requires column sort
    int     _nrow;    // number of rows in the colun -- useful for trim

    // OUT
    long   _rowcnt;
    double _sum;
   MeanNARMTask(boolean narm) {
     _narm = narm;
//     _trim = trim;
//     _nrow = nrow;
//     if (_trim != 0) {
//       _start = (long)Math.floor(_trim * (nrow - 1));
//       _end   = (long)(nrow - Math.ceil(_trim * (nrow - 1)));
//     }
   }
    @Override public void map(Chunk c) {
      if (c.vec().isEnum() || c.vec().isUUID()) { _sum = Double.NaN; _rowcnt = 0; return;}
      if (_narm) {
        for (int r = 0; r < c._len; r++)
          if (!c.isNA0(r)) { _sum += c.at0(r); _rowcnt++;}
      } else {
        for (int r = 0; r < c._len; r++)
          if (c.isNA0(r)) { _rowcnt = 0; _sum = Double.NaN; return; } else { _sum += c.at0(r); _rowcnt++; }
      }
    }
    @Override public void reduce(MeanNARMTask t) {
      _rowcnt += t._rowcnt;
      _sum += t._sum;
    }
  }
}

class ASTTable extends ASTUniPrefixOp {
  ASTTable() { super(new String[]{"table", "..."}); }
  @Override String opStr() { return "table"; }
  @Override ASTOp make() { return new ASTTable(); }

  @Override ASTTable parse_impl(Exec E) {
    AST ary = E.parse();
    AST two = E.skipWS().parse();
    if (two instanceof ASTString) two = new ASTNull();
    ASTTable res = (ASTTable)clone();
    res._asts = new AST[]{ary, two}; //two is pushed on, then ary is pushed on
    return res;
  }

  @Override void apply(Env env) {
    Frame two = env.peekType() == Env.NULL ? null : env.pop0Ary();
    if (two == null) env.pop();
    Frame one = env.pop0Ary();

    // Rules: two != null => two.numCols == one.numCols == 1
    //        two == null => one.numCols == 1 || one.numCols == 2
    // Anything else is IAE

    if (two != null)
      if (two.numCols() != 1 || one.numCols() != 1)
        throw new IllegalArgumentException("table supports at *most* two vectors");
    else
      if (one.numCols() < 1 || one.numCols() > 2 )
        throw new IllegalArgumentException("table supports at *most* two vectors and at least one vector.");

    Frame fr;
    if (two != null) fr = new Frame(one.add(two));
    else fr = one;

    int ncol;
    if ((ncol = fr.vecs().length) > 2)
      throw new IllegalArgumentException("table does not apply to more than two cols.");
    for (int i = 0; i < ncol; i++) if (!fr.vecs()[i].isInt())
      throw new IllegalArgumentException("table only applies to integer vectors.");
    String[][] domains = new String[ncol][];  // the domain names to display as row and col names
    // if vec does not have original domain, use levels returned by CollectDomain
    long[][] levels = new long[ncol][];
    for (int i = 0; i < ncol; i++) {
      Vec v = fr.vecs()[i];
      levels[i] = new Vec.CollectDomain().doAll(new Frame(v)).domain();
      domains[i] = v.domain();
    }
    long[][] counts = new Tabularize(levels).doAll(fr)._counts;
    // Build output vecs
    Key keys[] = Vec.VectorGroup.VG_LEN1.addVecs(counts.length+1);
    Vec[] vecs = new Vec[counts.length+1];
    String[] colnames = new String[counts.length+1];
    AppendableVec v0 = new AppendableVec(keys[0]);
    v0.setDomain(fr.vecs()[0].domain() == null ? null : fr.vecs()[0].domain().clone());
    NewChunk c0 = new NewChunk(v0,0);
    for( int i=0; i<levels[0].length; i++ ) c0.addNum((double) levels[0][i]);
    c0.close(0,null);
    vecs[0] = v0.close(null);
    colnames[0] = "row.names";
    if (ncol==1) colnames[1] = "Count";
    for (int level1=0; level1 < counts.length; level1++) {
      AppendableVec v = new AppendableVec(keys[level1+1]);
      NewChunk c = new NewChunk(v,0);
      v.setDomain(null);
      for (int level0=0; level0 < counts[level1].length; level0++)
        c.addNum((double) counts[level1][level0]);
      c.close(0, null);
      vecs[level1+1] = v.close(null);
      if (ncol>1) {
        colnames[level1+1] = domains[1]==null? Long.toString(levels[1][level1]) : domains[1][(int)(levels[1][level1])];
      }
    }
    Frame fr2 = new Frame(colnames, vecs);
    env.cleanup(fr, one, two);
    env.push(new ValFrame(fr2));
  }

  private static class Tabularize extends MRTask<Tabularize> {
    public final long[][]  _domains;
    public long[][] _counts;

    public Tabularize(long[][] dom) { super(); _domains=dom; }
    @Override public void map(Chunk[] cs) {
      assert cs.length == _domains.length;
      _counts = _domains.length==1? new long[1][] : new long[_domains[1].length][];
      for (int i=0; i < _counts.length; i++) _counts[i] = new long[_domains[0].length];
      for (int i=0; i < cs[0]._len; i++) {
        if (cs[0].isNA0(i)) continue;
        long ds[] = _domains[0];
        int level0 = Arrays.binarySearch(ds,cs[0].at80(i));
        assert 0 <= level0 && level0 < ds.length : "l0="+level0+", len0="+ds.length+", min="+ds[0]+", max="+ds[ds.length-1];
        int level1;
        if (cs.length>1) {
          if (cs[1].isNA0(i)) continue; else level1 = Arrays.binarySearch(_domains[1],(int)cs[1].at80(i));
          assert 0 <= level1 && level1 < _domains[1].length;
        } else {
          level1 = 0;
        }
        _counts[level1][level0]++;
      }
    }
    @Override public void reduce(Tabularize that) { ArrayUtils.add(_counts, that._counts); }
  }
}

// Selective return.  If the selector is a double, just eval both args and
// return the selected one.  If the selector is an array, then it must be
// compatible with argument arrays (if any), and the selection is done
// element-by-element.
//class ASTIfElse extends ASTOp {
//  static final String VARS[] = new String[]{"ifelse","tst","true","false"};
//  static Type[] newsig() {
//    Type t1 = Type.unbound(), t2 = Type.unbound(), t3=Type.unbound();
//    return new Type[]{Type.anyary(new Type[]{t1,t2,t3}),t1,t2,t3};
//  }
//  ASTIfElse( ) { super(VARS, newsig(),OPF_INFIX,OPP_PREFIX,OPA_RIGHT); }
//  @Override ASTOp make() {return new ASTIfElse();}
//  @Override String opStr() { return "ifelse"; }
//  // Parse an infix trinary ?: operator
//
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    // All or none are functions
//    assert ( env.isFcn(-1) &&  env.isFcn(-2) &&  _t.ret().isFcn())
//            ||   (!env.isFcn(-1) && !env.isFcn(-2) && !_t.ret().isFcn());
//    // If the result is an array, then one of the other of the two must be an
//    // array.  , and this is a broadcast op.
//    assert !_t.isAry() || env.isAry(-1) || env.isAry(-2);
//
//    // Single selection?  Then just pick slots
//    if( !env.isAry(-3) ) {
//      if( env.dbl(-3)==0 ) env.pop_into_stk(-4);
//      else {  env.pop();   env.pop_into_stk(-3); }
//      return;
//    }
//
//    Frame  frtst=null, frtru= null, frfal= null;
//    double  dtst=  0 ,  dtru=   0 ,  dfal=   0 ;
//    if( env.isAry() ) frfal= env.popAry(); else dfal = env.popDbl(); String kf = env.key();
//    if( env.isAry() ) frtru= env.popAry(); else dtru = env.popDbl(); String kt = env.key();
//    if( env.isAry() ) frtst= env.popAry(); else dtst = env.popDbl(); String kq = env.key();
//
//    // Multi-selection
//    // Build a doAll frame
//    Frame fr  = new Frame(frtst); // Do-All frame
//    final int  ncols = frtst.numCols(); // Result column count
//    final long nrows = frtst.numRows(); // Result row count
//    String names[]=null;
//    if( frtru !=null ) {          // True is a Frame?
//      if( frtru.numCols() != ncols ||  frtru.numRows() != nrows )
//        throw new IllegalArgumentException("Arrays must be same size: "+frtst+" vs "+frtru);
//      fr.add(frtru,true);
//      names = frtru._names;
//    }
//    if( frfal !=null ) {          // False is a Frame?
//      if( frfal.numCols() != ncols ||  frfal.numRows() != nrows )
//        throw new IllegalArgumentException("Arrays must be same size: "+frtst+" vs "+frfal);
//      fr.add(frfal,true);
//      names = frfal._names;
//    }
//    if( names==null && frtst!=null ) names = frtst._names;
//    final boolean t = frtru != null;
//    final boolean f = frfal != null;
//    final double fdtru = dtru;
//    final double fdfal = dfal;
//
//    // Run a selection picking true/false across the frame
//    Frame fr2 = new MRTask2() {
//      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
//        for( int i=0; i<nchks.length; i++ ) {
//          NewChunk n =nchks[i];
//          int off=i;
//          Chunk ctst=     chks[off];
//          Chunk ctru= t ? chks[off+=ncols] : null;
//          Chunk cfal= f ? chks[off+=ncols] : null;
//          int rlen = ctst._len;
//          for( int r=0; r<rlen; r++ )
//            if( ctst.isNA0(r) ) n.addNA();
//            else n.addNum(ctst.at0(r)!=0 ? (t ? ctru.at0(r) : fdtru) : (f ? cfal.at0(r) : fdfal));
//        }
//      }
//    }.doAll(ncols,fr).outputFrame(names,fr.domains());
//    env.subRef(frtst,kq);
//    if( frtru != null ) env.subRef(frtru,kt);
//    if( frfal != null ) env.subRef(frfal,kf);
//    env.pop();
//    env.push(fr2);
//  }
//}

class ASTCut extends ASTUniPrefixOp {
  String[] _labels = null;
  double[] _cuts;
  boolean _includelowest = false;
  boolean _right = true;
  double _diglab = 3;
  ASTCut() { super(new String[]{"cut", "ary", "breaks", "labels", "include.lowest", "right", "dig.lab"});}
  @Override String opStr() { return "cut"; }
  @Override ASTOp make() {return new ASTCut();}
  ASTCut parse_impl(Exec E) {
    AST ary = E.parse();
    // breaks first
    String[] cuts = E.skipWS().peek() == '{'
            ? E.xpeek('{').parseString('}').split(";")
            : E.peek() == '#' ? new String[]{Double.toString( ((ASTNum)E.parse()).dbl() )}
            : new String[]{E.parseString(E.peekPlus())};
    for (int i = 0; i < cuts.length; ++i) cuts[i] = cuts[i].replace("\"", "").replace("\'", "");
    _cuts = new double[cuts.length];
    for (int i = 0; i < cuts.length; ++i) _cuts[i] = Double.valueOf(cuts[i]);
    // labels second
    _labels = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : new String[]{E.parseString(E.peekPlus())};
    // cleanup _labels
    for (int i = 0; i < _labels.length; ++i) _labels[i] = _labels[i].replace("\"", "").replace("\'", "");
    if (_labels.length==1 && _labels[0].equals("null")) _labels = null;
    AST inc_lowest = E.skipWS().parse();
    inc_lowest = E._env.lookup((ASTId)inc_lowest);
    _includelowest = ((ASTNum)inc_lowest).dbl() == 1;
    AST right = E.skipWS().parse();
    right = E._env.lookup((ASTId)right);
    _right = ((ASTNum)right).dbl() == 1;
    ASTNum diglab = (ASTNum)E.skipWS().parse();
    _diglab = diglab.dbl();
    _diglab = _diglab >= 12 ? 12 : _diglab; // cap at 12 digits
    ASTCut res = (ASTCut) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  private String left() { return _right ? "(" : "["; }
  private String rite() { return _right ? "]" : ")"; }
  @Override void apply(Env env) {
    Frame fr = env.pop0Ary();
    if(fr.vecs().length != 1 || fr.vecs()[0].isEnum())
      throw new IllegalArgumentException("First argument must be a numeric column vector");

    double fmin = fr.anyVec().min();
    double fmax = fr.anyVec().max();

    int nbins = _cuts.length - 1;  // c(0,10,100) -> 2 bins (0,10] U (10, 100]
    double width;
    if (nbins == 0) {
      if (_cuts[0] < 2) throw new IllegalArgumentException("The number of cuts must be >= 2. Got: "+_cuts[0]);
      // in this case, cut the vec into _cuts[0] many pieces of equal length
      nbins = (int) Math.floor(_cuts[0]);
      width = (fmax - fmin)/nbins;
      _cuts = new double[nbins];
      _cuts[0] = fmin - 0.001*(fmax - fmin);
      for (int i = 1; i < _cuts.length; ++i) _cuts[i] = (i == _cuts.length-1) ? (fmax + 0.001*(fmax-fmin))  : (fmin + i*width);
    }
    width = (fmax - fmin)/nbins;
    if(width == 0) throw new IllegalArgumentException("Data vector is constant!");
    if (_labels != null && _labels.length != nbins) throw new IllegalArgumentException("`labels` vector does not match the number of cuts.");

    // Construct domain names from _labels or bin intervals if _labels is null
    final double cuts[] = _cuts;

    // first round _cuts to dig.lab decimals: example floor(2.676*100 + 0.5) / 100
    for (int i = 0; i < _cuts.length; ++i) _cuts[i] = Math.floor(_cuts[i] * Math.pow(10,_diglab) + 0.5) / Math.pow(10,_diglab);

    String[][] domains = new String[1][nbins];
    if (_labels == null) {
      domains[0][0] = (_includelowest ? "[" : left()) + _cuts[0] + "," + _cuts[1] + rite();
      for (int i = 1; i < (_cuts.length - 1); ++i)  domains[0][i] = left() + _cuts[i] + "," + _cuts[i+1] + rite();
    } else domains[0] = _labels;

    final boolean incLow = _includelowest;
    Frame fr2 = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        int rows = c._len;
        for (int r = 0; r < rows; ++r) {
          double x = c.at0(r);
          if (Double.isNaN(x) || (incLow  && x <  cuts[0])
                              || (!incLow && x <= cuts[0])
                              || (_right  && x >  cuts[cuts.length-1])
                              || (!_right && x >= cuts[cuts.length-1])) nc.addNum(Double.NaN);
          else {
            for (int i = 1; i < cuts.length; ++i) {
              if (_right) {
                if (x <= cuts[i]) {
                  nc.addNum(i - 1);
                  break;
                }
              } else if (x < cuts[i]) { nc.addNum(i-1); break; }
            }
          }
        }
      }
    }.doAll(1, fr).outputFrame(fr.names(), domains);

    env.cleanup(fr);
    env.push(new ValFrame(fr2));
  }
}

class ASTFactor extends ASTUniPrefixOp {
  ASTFactor() { super(new String[]{"", "ary"});}
  @Override String opStr() { return "as.factor"; }
  @Override ASTOp make() {return new ASTFactor();}
  ASTFactor parse_impl(Exec E) {
    AST ary = E.parse();
    ASTFactor res = (ASTFactor) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame ary = env.pop0Ary(); // pop w/o lowering refs
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("factor requires a single column");
    Vec v0 = ary.anyVec();
    Vec v1 = v0.isEnum() ? null : v0.toEnum(); // toEnum() creates a new vec --> must be cleaned up!
    Frame fr = new Frame(ary._names, new Vec[]{v1 == null ? v0.makeCopy() : v1});
//    env.cleanup(ary);
    env.push(new ValFrame(fr));
  }
}

/**
* R 'ls' command.
*
* This method is purely for the console right now.  Print stuff into the string buffer.
* JSON response is not configured at all.
*/
class ASTLs extends ASTOp {
  ASTLs() { super(new String[]{"ls"}); }
  @Override String opStr() { return "ls"; }
  @Override ASTOp make() {return new ASTLs();}
  ASTLs parse_impl(Exec E) { return (ASTLs) clone(); }
  @Override void apply(Env env) {
    ArrayList<String> domain = new ArrayList<>();
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
    NewChunk keys = new NewChunk(av,0);
    int r = 0;
//    KeySnapshot.KeyInfo[] infos = KeySnapshot.globalSnapshot()._keyInfos;
    for( Key key : KeySnapshot.globalSnapshot().keys())
      if( key.user_allowed() && H2O.get(key) != null) {
        if (DKV.get(key).get() instanceof Job.Progress) continue;
        keys.addEnum(r++);
        domain.add(key.toString());
      }
    keys.close(fs);
    Vec c0 = av.close(fs);   // c0 is the row index vec
    fs.blockForPending();
    String[] key_domain = new String[domain.size()];
    for (int i = 0; i < key_domain.length; ++i) key_domain[i] = domain.get(i);
    c0.setDomain(key_domain);
    Frame ls = new Frame(Key.make("h2o_ls"), new String[]{"key"}, new Vec[]{c0});
    env.push(new ValFrame(ls));
  }

  private double getSize(Key k) {
    return (double)(((Frame) k.get()).byteSize());
//    if (k.isChunkKey()) return (double)((Chunk)DKV.get(k).get()).byteSize();
//    if (k.isVec()) return (double)((Vec)DKV.get(k).get()).rollupStats()._size;
//    return Double.NaN;
  }
}



// WIP

//class ASTXorSum extends ASTReducerOp { ASTXorSum() {super(0,false); }
//  @Override String opStr(){ return "xorsum";}
//  @Override ASTOp make() {return new ASTXorSum();}
//  @Override double op(double d0, double d1) {
//    long d0Bits = Double.doubleToLongBits(d0);
//    long d1Bits = Double.doubleToLongBits(d1);
//    long xorsumBits = d0Bits ^ d1Bits;
//    // just need to not get inf or nan. If we zero the upper 4 bits, we won't
//    final long ZERO_SOME_SIGN_EXP = 0x0fffffffffffffffL;
//    xorsumBits = xorsumBits & ZERO_SOME_SIGN_EXP;
//    double xorsum = Double.longBitsToDouble(xorsumBits);
//    return xorsum;
//  }
//  @Override double[] map(Env env, double[] in, double[] out) {
//    if (out == null || out.length < 1) out = new double[1];
//    long xorsumBits = 0;
//    long vBits;
//    // for dp ieee 754 , sign and exp are the high 12 bits
//    // We don't want infinity or nan, because h2o will return a string.
//    double xorsum = 0;
//    for (double v : in) {
//      vBits = Double.doubleToLongBits(v);
//      xorsumBits = xorsumBits ^ vBits;
//    }
//    // just need to not get inf or nan. If we zero the upper 4 bits, we won't
//    final long ZERO_SOME_SIGN_EXP = 0x0fffffffffffffffL;
//    xorsumBits = xorsumBits & ZERO_SOME_SIGN_EXP;
//    xorsum = Double.longBitsToDouble(xorsumBits);
//    out[0] = xorsum;
//    return out;
//  }
//}


// Legacy Items: On the chopping block


// Brute force implementation of matrix multiply
//class ASTMMult extends ASTOp {
//  @Override String opStr() { return "%*%"; }
//  ASTMMult( ) {
//    super(new String[]{"", "x", "y"},
//            new Type[]{Type.ARY,Type.ARY,Type.ARY},
//            OPF_PREFIX,
//            OPP_MUL,
//            OPA_RIGHT);
//  }
//  @Override ASTOp make() { return new ASTMMult(); }
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    env.poppush(3,new Matrix(env.ary(-2)).mult(env.ary(-1)),null);
//  }
//}
//
//// Brute force implementation of matrix transpose
//class ASTMTrans extends ASTOp {
//  @Override String opStr() { return "t"; }
//  ASTMTrans( ) {
//    super(new String[]{"", "x"},
//            new Type[]{Type.ARY,Type.dblary()},
//            OPF_PREFIX,
//            OPP_PREFIX,
//            OPA_RIGHT);
//  }
//  @Override ASTOp make() { return new ASTMTrans(); }
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    if(!env.isAry(-1)) {
//      Key k = new Vec.VectorGroup().addVec();
//      Futures fs = new Futures();
//      AppendableVec avec = new AppendableVec(k);
//      NewChunk chunk = new NewChunk(avec, 0);
//      chunk.addNum(env.dbl(-1));
//      chunk.close(0, fs);
//      Vec vec = avec.close(fs);
//      fs.blockForPending();
//      vec._domain = null;
//      Frame fr = new Frame(new String[] {"C1"}, new Vec[] {vec});
//      env.poppush(2,new Matrix(fr).trans(),null);
//    } else
//      env.poppush(2,new Matrix(env.ary(-1)).trans(),null);
//  }
//}


//class ASTPrint extends ASTOp {
//  static Type[] newsig() {
//    Type t1 = Type.unbound();
//    return new Type[]{t1, t1, Type.varargs(Type.unbound())};
//  }
//  ASTPrint() { super(new String[]{"print", "x", "y..."},
//          newsig(),
//          OPF_PREFIX,
//          OPP_PREFIX,OPA_RIGHT); }
//  @Override String opStr() { return "print"; }
//  @Override ASTOp make() {return new ASTPrint();}
//  @Override void apply(Env env, int argcnt, ASTApply apply) {
//    for( int i=1; i<argcnt; i++ ) {
//      if( env.isAry(i-argcnt) ) {
//        env._sb.append(env.ary(i-argcnt).toStringAll());
//      } else {
//        env._sb.append(env.toString(env._sp+i-argcnt,true));
//      }
//    }
//    env.pop(argcnt-2);          // Pop most args
//    env.pop_into_stk(-2);       // Pop off fcn, returning 1st arg
//  }
//}


// Variable length; flatten all the component arys
//class ASTCat extends ASTUniPrefixOp {
//  protected static int _argcnt;
//  @Override String opStr() { return "c"; }
//  ASTCat( ) { super(new String[]{"cat","dbls", "..."});}
//  @Override ASTOp make() {return new ASTCat();}
//  @Override ASTCat parse_impl(Exec E) {
//    ArrayList<AST> dblarys = new ArrayList<>();
//    AST a;
//    while (true) {
//      a = E.skipWS().parse();
//      if (a instanceof ASTId) {
//        AST ast = E._env.lookup((ASTId)a);
//        if (ast instanceof ASTFrame) {dblarys.add(a); continue; } else break;
//      }
//      if (a instanceof ASTNum || a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTBinOp || a instanceof ASTUniOp || a instanceof ASTReducerOp)
//        dblarys.add(a);
//      else break;
//    }
//    ASTCat res = (ASTCat) clone();
//    AST[] arys = new AST[_argcnt = dblarys.size()];
//    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
//    res._asts = arys;
//    return res;
//  }
//
//  @Override double[] map(Env env, double[] in, double[] out) {
//    if (out == null || out.length < in.length) out = new double[in.length];
//    System.arraycopy(in, 0, out, 0, in.length);
//    return out;
//  }
//  @Override void apply(Env env) {
//    Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
//    AppendableVec av = new AppendableVec(key);
//    NewChunk nc = new NewChunk(av,0);
//    int argcnt = _argcnt;
//    for( int i=0; i<argcnt; i++ ) {
//      if (env.isAry()) {
//
//      } else {
//        nc.addNum(env.popDbl());
//      }
//
//      if (env.isAry(i-argcnt+1)) for (Vec vec : env.ary(i-argcnt+1).vecs()) {
//        if (vec.nChunks() > 1) H2O.unimpl();
//        for (int r = 0; r < vec.length(); r++) nc.addNum(vec.at(r));
//      }
//      else nc.addNum(env.dbl(i-argcnt+1));
//    }
//    nc.close(0,null);
//    Vec v = av.close(null);
//    env.pop(argcnt);
//    env.push(new Frame(new String[]{"C1"}, new Vec[]{v}));
//  }
//}


//class ASTFindInterval extends ASTUniPrefixOp {
//  protected static boolean _rclosed;
//  protected static double _x;
//
//  ASTFindInterval() { super(new String[]{"findInterval", "x", "vec", "rightmost.closed"}); }
//  @Override String opStr() { return "findInterval"; }
//  @Override ASTOp make() { return new ASTFindInterval(); }
//  @Override ASTFindInterval parse_impl(Exec E) {
//    // First argument must be a num, anything else throw IAE
//    AST x = E.skipWS().parse();
//    if (! (x instanceof ASTNum)) throw new IllegalArgumentException("First argument to findInterval must be a single number. Got: " + x.toString());
//    _x =  ((ASTNum)(E.skipWS().parse())).dbl();
//    // Get the ary
//    AST ary = E.parse();
//    // Get the rightmost.closed
//    AST a = E._env.lookup((ASTId)E.skipWS().parse());
//    _rclosed = ((ASTNum)a).dbl() == 1;
//    // Finish the rest
//    ASTFindInterval res = (ASTFindInterval) clone();
//    res._asts = new AST[]{ary};
//    return res;
//  }
//
//  @Override void apply(Env env) {
//    final boolean rclosed = _rclosed;
//
//    if(env.isNum()) {
//      final double cutoff = _x;
//
//      Frame fr = env.pop0Ary();
//      if(fr.numCols() != 1 || fr.vecs()[0].isEnum())
//        throw new IllegalArgumentException("Argument must be a single numeric column vector. Got an array with " + fr.numCols() + " columns. Column was an enum: " + fr.vecs()[0].isEnum());
//
//      Frame fr2 = new MRTask() {
//        @Override public void map(Chunk chk, NewChunk nchk) {
//          for(int r = 0; r < chk._len; r++) {
//            double x = chk.at0(r);
//            if(Double.isNaN(x))
//              nchk.addNum(Double.NaN);
//            else {
//              if(rclosed)
//                nchk.addNum(x > cutoff ? 1 : 0);   // For rightmost.closed = TRUE
//              else
//                nchk.addNum(x >= cutoff ? 1 : 0);
//            }
//          }
//        }
//      }.doAll(1,fr).outputFrame(fr._names, fr.domains());
//      env.subRef(fr, skey);
//      env.pop();
//      env.push(fr2);
//    } else if(env.isAry()) {
//      Frame ary = env.popAry();
//      String skey1 = env.key();
//      if(ary.vecs().length != 1 || ary.vecs()[0].isEnum())
//        throw new IllegalArgumentException("Second argument must be a numeric column vector");
//      Vec brks = ary.vecs()[0];
//      // TODO: Check that num rows below some cutoff, else this will likely crash
//
//      // Check if vector of cutoffs is sorted in weakly ascending order
//      final int len = (int)brks.length();
//      final double[] cutoffs = new double[len];
//      for(int i = 0; i < len-1; i++) {
//        if(brks.at(i) > brks.at(i+1))
//          throw new IllegalArgumentException("Second argument must be sorted in non-decreasing order");
//        cutoffs[i] = brks.at(i);
//      }
//      cutoffs[len-1] = brks.at(len-1);
//
//      Frame fr = env.popAry();
//      String skey2 = env.key();
//      if(fr.vecs().length != 1 || fr.vecs()[0].isEnum())
//        throw new IllegalArgumentException("First argument must be a numeric column vector");
//
//      Frame fr2 = new MRTask2() {
//        @Override public void map(Chunk chk, NewChunk nchk) {
//          for(int r = 0; r < chk._len; r++) {
//            double x = chk.at0(r);
//            if(Double.isNaN(x))
//              nchk.addNum(Double.NaN);
//            else {
//              double n = Arrays.binarySearch(cutoffs, x);
//              if(n < 0) nchk.addNum(-n-1);
//              else if(rclosed && n == len-1) nchk.addNum(n);   // For rightmost.closed = TRUE
//              else nchk.addNum(n+1);
//            }
//          }
//        }
//      }.doAll(1,fr).outputFrame(fr._names, fr.domains());
//      env.subRef(ary, skey1);
//      env.subRef(fr, skey2);
//      env.pop();
//      env.push(fr2);
//    }
//  }
//}
