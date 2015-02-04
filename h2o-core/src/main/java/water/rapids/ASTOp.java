package water.rapids;

//import hex.Quantiles;

import hex.DMatrix;
import jsr166y.CountedCompleter;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import org.joda.time.DateTime;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.fvec.*;
import water.parser.ParseTime;
import water.parser.ValueString;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

//import hex.la.Matrix;
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
    SYMBOLS.put(",", new ASTStatement());
    SYMBOLS.put("=", new ASTAssign());
    SYMBOLS.put("'", new ASTString('\'', ""));
    SYMBOLS.put("\"",new ASTString('\"', ""));
    SYMBOLS.put("%", new ASTId('%', ""));
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
    SYMBOLS.put("not", new ASTNot());
    SYMBOLS.put("_", new ASTNot());
    SYMBOLS.put("if", new ASTIf());
    SYMBOLS.put("else", new ASTElse());
    SYMBOLS.put("for", new ASTFor());
    SYMBOLS.put("while", new ASTWhile());
    SYMBOLS.put("return", new ASTReturn());
    SYMBOLS.put("del", new ASTDelete());
    SYMBOLS.put("x", new ASTMMult());
    SYMBOLS.put("t", new ASTTranspose());

    //TODO: Have `R==` type methods (also `py==`, `js==`, etc.)

    // Unary infix ops
    putUniInfix(new ASTNot());
    // Binary infix ops
    putBinInfix(new ASTPlus());
    putBinInfix(new ASTSub());
    putBinInfix(new ASTMul());
    putBinInfix(new ASTMMult());
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
    putPrefix(new ASTLog10 ());
    putPrefix(new ASTLog2 ());
    putPrefix(new ASTLog1p ());
    putPrefix(new ASTExp ());
    putPrefix(new ASTExpm1 ());
    putPrefix(new ASTGamma());
    putPrefix(new ASTLGamma());
    putPrefix(new ASTDiGamma());
    putPrefix(new ASTTriGamma());
    putPrefix(new ASTScale());
    putPrefix(new ASTCharacter());
    putPrefix(new ASTFactor());
    putPrefix(new ASTIsFactor());
    putPrefix(new ASTAnyFactor());              // For Runit testing
    putPrefix(new ASTCanBeCoercedToLogical());
    putPrefix(new ASTAnyNA());
    putPrefix(new ASTRound());
    putPrefix(new ASTSignif());
    putPrefix(new ASTTrun());

    putPrefix(new ASTTranspose());

    // Trigonometric functions
    putPrefix(new ASTCos  ());
    putPrefix(new ASTSin  ());
    putPrefix(new ASTTan  ());
    putPrefix(new ASTACos ());
    putPrefix(new ASTASin ());
    putPrefix(new ASTATan ());
    putPrefix(new ASTCosh ());
    putPrefix(new ASTSinh ());
    putPrefix(new ASTTanh ());
    putPrefix(new ASTACosh());
    putPrefix(new ASTASinh());
    putPrefix(new ASTATanh());
    putPrefix(new ASTCosPi());
    putPrefix(new ASTSinPi());
    putPrefix(new ASTTanPi());
    

    // More generic reducers
    putPrefix(new ASTMin ());
    putPrefix(new ASTMax ());
    putPrefix(new ASTSum ());
    putPrefix(new ASTSdev());
    putPrefix(new ASTVar ());
    putPrefix(new ASTMean());

    // Misc
    putPrefix(new ASTMatch ());
    putPrefix(new ASTRename());  //TODO
    putPrefix(new ASTSeq   ());  //TODO
    putPrefix(new ASTSeqLen());  //TODO
    putPrefix(new ASTRepLen());  //TODO
    putPrefix(new ASTQtile ());  //TODO
    putPrefix(new ASTCbind ());
    putPrefix(new ASTRbind ());
    putPrefix(new ASTTable ());
//    putPrefix(new ASTReduce());
    putPrefix(new ASTIfElse());
    putPrefix(new ASTApply ());
    putPrefix(new ASTSApply());
    putPrefix(new ASTddply ());
//    putPrefix(new ASTUnique());
    putPrefix(new ASTXorSum());
    putPrefix(new ASTRunif ());
    putPrefix(new ASTCut   ());
    putPrefix(new ASTLs    ());
    putPrefix(new ASTSetColNames());

    // Date
    putPrefix(new ASTasDate());

//Classes that may not come back:

//    putPrefix(new ASTfindInterval());
//    putPrefix(new ASTPrint ());
    putPrefix(new ASTCat   ());
//Time extractions, to and from msec since the Unix Epoch
    putPrefix(new ASTYear  ());
    putPrefix(new ASTMonth ());
    putPrefix(new ASTDay   ());
    putPrefix(new ASTHour  ());
    putPrefix(new ASTMinute());
    putPrefix(new ASTSecond());
    putPrefix(new ASTMillis());

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
  double[] map(Env env, double[] in, double[] out, AST[] args) { throw H2O.unimpl(); }
  @Override void exec(Env e) { throw H2O.fail(); }
  // special exec for apply calls
  void exec(Env e, AST arg1, AST[] args) { throw H2O.unimpl("No exec method for `" + this.opStr() + "` during `apply` call"); }
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
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    AST arg = E.parse();
    if (arg instanceof ASTId) arg = Env.staticLookup((ASTId)arg);
    ASTUniOp res = (ASTUniOp) clone();
    res._asts = new AST[]{arg};
    return res;
  }

  @Override void exec(Env e, AST arg1, AST[] args) {
    if (args != null) throw new IllegalArgumentException("Too many arguments passed to `"+opStr()+"`");
    arg1.exec(e);
    if (e.isAry()) e._global._frames.put(Key.make().toString(), e.peekAry());
    apply(e);
  }

  @Override void apply(Env env) {
    // Expect we can broadcast across all functions as needed.
    if( env.isNum() ) { env.push(new ValNum(op(env.popDbl()))); return; }
//    if( env.isStr() ) { env.push(new ASTString(op(env.popStr()))); return; }
    Frame fr = env.popAry();
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
              n.addNum(uni.op(c.atd(r)));
          }
        }
      }
    }.doAll(fr.numCols(),fr).outputFrame(Key.make(), fr._names, null);
    env.pushAry(fr2);
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
class ASTACosh extends ASTUniPrefixOp { @Override String opStr(){ return "acosh"; } @Override ASTOp make() {return new ASTACosh ();} @Override double op(double d) { return FastMath.acosh(d);}}
class ASTASinh extends ASTUniPrefixOp { @Override String opStr(){ return "asinh"; } @Override ASTOp make() {return new ASTASinh ();} @Override double op(double d) { return FastMath.asinh(d);}}
class ASTATanh extends ASTUniPrefixOp { @Override String opStr(){ return "atanh"; } @Override ASTOp make() {return new ASTATanh ();} @Override double op(double d) { return FastMath.atanh(d);}}
class ASTCosPi extends ASTUniPrefixOp { @Override String opStr(){ return "cospi"; } @Override ASTOp make() {return new ASTCosPi ();} @Override double op(double d) { return Math.cos(Math.PI*d);}}
class ASTSinPi extends ASTUniPrefixOp { @Override String opStr(){ return "sinpi"; } @Override ASTOp make() {return new ASTSinPi ();} @Override double op(double d) { return Math.sin(Math.PI*d);}}
class ASTTanPi extends ASTUniPrefixOp { @Override String opStr(){ return "tanpi"; } @Override ASTOp make() {return new ASTTanPi ();} @Override double op(double d) { return Math.tan(Math.PI*d);}}
class ASTAbs  extends ASTUniPrefixOp { @Override String opStr(){ return "abs";  } @Override ASTOp make() {return new ASTAbs ();} @Override double op(double d) { return Math.abs(d);}}
class ASTSgn  extends ASTUniPrefixOp { @Override String opStr(){ return "sign" ; } @Override ASTOp make() {return new ASTSgn ();} @Override double op(double d) { return Math.signum(d);}}
class ASTSqrt extends ASTUniPrefixOp { @Override String opStr(){ return "sqrt"; } @Override ASTOp make() {return new ASTSqrt();} @Override double op(double d) { return Math.sqrt(d);}}
class ASTTrun extends ASTUniPrefixOp { @Override String opStr(){ return "trunc"; } @Override ASTOp make() {return new ASTTrun();} @Override double op(double d) { return d>=0?Math.floor(d):Math.ceil(d);}}
class ASTCeil extends ASTUniPrefixOp { @Override String opStr(){ return "ceiling"; } @Override ASTOp make() {return new ASTCeil();} @Override double op(double d) { return Math.ceil(d);}}
class ASTFlr  extends ASTUniPrefixOp { @Override String opStr(){ return "floor";} @Override ASTOp make() {return new ASTFlr ();} @Override double op(double d) { return Math.floor(d);}}
class ASTLog  extends ASTUniPrefixOp { @Override String opStr(){ return "log";  } @Override ASTOp make() {return new ASTLog ();} @Override double op(double d) { return Math.log(d);}}
class ASTLog10  extends ASTUniPrefixOp { @Override String opStr(){ return "log10";  } @Override ASTOp make() {return new ASTLog10 ();} @Override double op(double d) { return Math.log10(d);}}
class ASTLog2  extends ASTUniPrefixOp { @Override String opStr(){ return "log2";  } @Override ASTOp make() {return new ASTLog2 ();} @Override double op(double d) { return Math.log(d)/Math.log(2);}}
class ASTLog1p  extends ASTUniPrefixOp { @Override String opStr(){ return "log1p";  } @Override ASTOp make() {return new ASTLog1p ();} @Override double op(double d) { return Math.log1p(d);}}
class ASTExp  extends ASTUniPrefixOp { @Override String opStr(){ return "exp";  } @Override ASTOp make() {return new ASTExp ();} @Override double op(double d) { return Math.exp(d);}}
class ASTExpm1  extends ASTUniPrefixOp { @Override String opStr(){ return "expm1";  } @Override ASTOp make() {return new ASTExpm1 ();} @Override double op(double d) { return Math.expm1(d);}}
class ASTGamma  extends ASTUniPrefixOp { @Override String opStr(){ return "gamma";  } @Override ASTOp make() {return new ASTGamma ();} @Override double op(double d) {  return Gamma.gamma(d);}}
class ASTLGamma extends ASTUniPrefixOp { @Override String opStr(){ return "lgamma"; } @Override ASTOp make() {return new ASTLGamma ();} @Override double op(double d) { return Gamma.logGamma(d);}}
class ASTDiGamma  extends ASTUniPrefixOp { @Override String opStr(){ return "digamma";  } @Override ASTOp make() {return new ASTDiGamma ();} @Override double op(double d) {  return Gamma.digamma(d);}}
class ASTTriGamma  extends ASTUniPrefixOp { @Override String opStr(){ return "trigamma";  } @Override ASTOp make() {return new ASTTriGamma ();} @Override double op(double d) {  return Gamma.trigamma(d);}}

class ASTIsNA extends ASTUniPrefixOp { @Override String opStr(){ return "is.na";} @Override ASTOp make() { return new ASTIsNA();} @Override double op(double d) { return Double.isNaN(d)?1:0;}
  @Override void apply(Env env) {
    // Expect we can broadcast across all functions as needed.
    if( env.isNum() ) { env.push(new ValNum(op(env.popDbl()))); return; }
    Frame fr = env.popAry();
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        for( int i=0; i<nchks.length; i++ ) {
          NewChunk n = nchks[i];
          Chunk c = chks[i];
          int rlen = c._len;
          for( int r=0; r<rlen; r++ )
            n.addNum( c.isNA(r) ? 1 : 0);
        }
      }
    }.doAll(fr.numCols(),fr).outputFrame(Key.make(), fr._names, null);
    env.pushAry(fr2);
  }
}

class ASTasDate extends ASTOp {
  protected static String _format;
  ASTasDate() { super(new String[]{"as.Date", "x", "format"}); }
  @Override String opStr() { return "as.Date"; }
  @Override ASTOp make() {return new ASTasDate();}
  @Override ASTasDate parse_impl(Exec E) {
    AST ast = E.parse();
    if (ast instanceof ASTId) ast = Env.staticLookup((ASTId)ast);
    try {
      _format = ((ASTString)E.skipWS().parse())._s;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("`format` must be a string.");
    }
    ASTasDate res = (ASTasDate) clone();
    res._asts = new AST[]{ast};
    return res;
  }
  @Override void apply(Env env) {
    final String format = _format;
    if (format.isEmpty()) throw new IllegalArgumentException("as.Date requires a non-empty format string");
    // check the format string more?

    Frame fr = env.popAry();

    if( fr.vecs().length != 1 || !fr.vecs()[0].isEnum() )
      throw new IllegalArgumentException("as.Date requires a single column of factors");

    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        //done on each node in lieu of rewriting DateTimeFormatter as Iced
        DateTimeFormatter dtf = ParseTime.forStrptimePattern(format).withZone(ParseTime.getTimezone());
        for( int i=0; i<nchks.length; i++ ) {
          NewChunk n =nchks[i];
          Chunk c = chks[i];
          int rlen = c._len;
          for( int r=0; r<rlen; r++ ) {
            if (!c.isNA(r)) {
              String date = c.vec().domain()[(int)c.atd(r)];
              n.addNum(DateTime.parse(date, dtf).getMillis(), 0);
            } else n.addNA();
          }
        }
      }
    }.doAll(fr.numCols(),fr).outputFrame(fr._names, null);
    env.pushAry(fr2);
  }
}

class ASTRound extends ASTUniPrefixOp {
  int _digits = 0;
  @Override String opStr() { return "round"; }
  ASTRound() { super(new String[]{"round", "x", "digits"}); }
  @Override ASTRound parse_impl(Exec E) {
    // Get the ary
    if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // Get the digits
    if (!(E.skipWS().hasNext())) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    try {
      _digits = (int) ((ASTNum) (E.parse())).dbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Expected a number for `digits` argument.");
    }
    ASTRound res = (ASTRound) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override ASTOp make() { return new ASTRound(); }
  @Override void apply(Env env) {
    final int digits = _digits;
    if(env.isAry()) {
      Frame fr = env.popAry();
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
              n.addNum(roundDigits(c.atd(r),digits));
          }
        }
      }.doAll(fr.numCols(),fr).outputFrame(fr.names(),fr.domains());
      env.pushAry(fr2);
    }
    else
      env.push(new ValNum(roundDigits(env.popDbl(), digits)));
  }

  // e.g.: floor(2.676*100 + 0.5) / 100 => 2.68
  static double roundDigits(double x, int digits) {
    if(Double.isNaN(x)) return x;
    double sgn = x < 0 ? -1 : 1;
    x = Math.abs(x);
    double power_of_10 = (int)Math.pow(10, digits);
    return sgn*(digits == 0
            // go to the even digit
            ? (x % 1 >= 0.5 && !(Math.floor(x)%2==0))
              ? Math.ceil(x)
              : Math.floor(x)
            : Math.floor(x * power_of_10 + 0.5) / power_of_10);
  }
}

class ASTSignif extends ASTUniPrefixOp {
  int _digits = 6;  // R default
  @Override String opStr() { return "signif"; }
  ASTSignif() { super(new String[]{"signif", "x", "digits"}); }
  @Override ASTSignif parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // Get the digits
    try {
      _digits = (int) ((ASTNum) (E.parse())).dbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Expected a double for `digits` argument.");
    }
    ASTSignif res = (ASTSignif) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override ASTOp make() { return new ASTSignif(); }
  @Override void apply(Env env) {
    final int digits = _digits;
    if(digits < 0)
      throw new IllegalArgumentException("Error in signif: argument digits must be a non-negative integer");

    if(env.isAry()) {
      Frame fr = env.popAry();
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
              n.addNum(signifDigits(c.atd(r),digits));
          }
        }
      }.doAll(fr.numCols(),fr).outputFrame(fr.names(),fr.domains());
      env.pushAry(fr2);
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
  public ASTNrow() { super(VARS1); }
  @Override String opStr() { return "nrow"; }
  @Override ASTOp make() {return new ASTNrow();}
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    double d = fr.numRows();
    env.push(new ValNum(d));
  }
}

class ASTNcol extends ASTUniPrefixOp {
  ASTNcol() { super(VARS1); }
  @Override String opStr() { return "ncol"; }
  @Override ASTOp make() {return new ASTNcol();}
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    double d = fr.numCols();
    env.push(new ValNum(d));
  }
}

class ASTLength extends ASTUniPrefixOp {
  ASTLength() { super(VARS1); }
  @Override String opStr() { return "length"; }
  @Override ASTOp make() { return new ASTLength(); }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    double d = fr.numCols() == 1 ? fr.numRows() : fr.numCols();
//    env.cleanup(fr);
//    env.clean();
    env.push(new ValNum(d));
  }
}

class ASTIsFactor extends ASTUniPrefixOp {
  ASTIsFactor() { super(VARS1); }
  @Override String opStr() { return "is.factor"; }
  @Override ASTOp make() {return new ASTIsFactor();}
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    String res = "FALSE";
    if (fr.numCols() != 1) throw new IllegalArgumentException("is.factor applies to a single column.");
    if (fr.anyVec().isEnum()) res = "TRUE";
    env.push(new ValStr(res));
  }
}

// Added to facilitate Runit testing
class ASTAnyFactor extends ASTUniPrefixOp {
  ASTAnyFactor() { super(VARS1);}
  @Override String opStr() { return "any.factor"; }
  @Override ASTOp make() {return new ASTAnyFactor();}
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    String res = "FALSE";
    for (int i = 0; i < fr.vecs().length; ++i)
      if (fr.vecs()[i].isEnum()) { res = "TRUE"; break; }
    env.push(new ValStr(res));
  }
}

class ASTCanBeCoercedToLogical extends ASTUniPrefixOp {
  ASTCanBeCoercedToLogical() { super(VARS1); }
  @Override String opStr() { return "canBeCoercedToLogical"; }
  @Override ASTOp make() {return new ASTCanBeCoercedToLogical();}
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    String res = "FALSE";
    Vec[] v = fr.vecs();
    for (Vec aV : v)
      if (aV.isInt())
        if (aV.min() == 0 && aV.max() == 1) { res = "TRUE"; break; }
    env.push(new ValStr(res));
  }
}

class ASTAnyNA extends ASTUniPrefixOp {
  ASTAnyNA() { super(VARS1); }
  @Override String opStr() { return "any.na"; }
  @Override ASTOp make() {return new ASTAnyNA();}
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    String res = "FALSE";
    for (int i = 0; i < fr.vecs().length; ++i)
      if (fr.vecs()[i].naCnt() > 0) { res = "TRUE"; break; }
    env.push(new ValStr(res));
  }
}

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
  @Override ASTOp make() {return new ASTScale();}
  ASTScale parse_impl(Exec E) {
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    parseArg(E, true);  // centers parse
    parseArg(E, false); // scales parse
    ASTScale res = (ASTScale) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  private void parseArg(Exec E, boolean center) {
    if (center) {
      if (!E.skipWS().hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
      String[] centers = E.peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
      if (centers == null) {
        // means `center` is boolean
        AST a;
        try {
          a = E._env.lookup((ASTId) E.skipWS().parse());
        } catch (ClassCastException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Expected to get an ASTId. Badly formed AST.");
        }
        try {
          _center = ((ASTNum) a).dbl() == 1;
          _centers = null;
        } catch (ClassCastException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Expected to get a number for the `center` argument.");
        }
      } else {
        for (int i = 0; i < centers.length; ++i) centers[i] = centers[i].replace("\"", "").replace("\'", "");
        _centers = new double[centers.length];
        for (int i = 0; i < centers.length; ++i) _centers[i] = Double.valueOf(centers[i]);
      }
    } else {
      if (!E.skipWS().hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
      String[] centers = E.peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
      if (centers == null) {
        // means `scale` is boolean
        AST a;
        try {
          a = E._env.lookup((ASTId) E.skipWS().parse());
        } catch (ClassCastException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Expected to get an ASTId. Badly formed AST.");
        }
        try {
          _scale = ((ASTNum) a).dbl() == 1;
          _scales = null;
        } catch (ClassCastException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Expected to get a number for the `scale` argument.");
        }
      } else {
        for (int i = 0; i < centers.length; ++i) centers[i] = centers[i].replace("\"", "").replace("\'", "");
        _scales = new double[centers.length];
        for (int i = 0; i < centers.length; ++i) _scales[i] = Double.valueOf(centers[i]);
      }
    }
  }

  @Override void apply(Env env) {
    Frame fr = env.popAry();
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
      env.pushAry(fr);
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
              double numer = cs[c].atd(r) - (use_mean
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
              double denom = cs[c].atd(r) / (use_rms
                      ? rms[c] : use_sig ? cs[c].vec().sigma()
                      : scales == null ? 1 : scales[c]);
              ncs[c].addNum(denom);
            }
        }
      }.doAll(centered.numCols(), centered).outputFrame(centered.names(), centered.domains());
    }
    env.pushAry(scaled);
  }
}

abstract class ASTTimeOp extends ASTOp {
  ASTTimeOp() { super(VARS1); }

  @Override ASTTimeOp parse_impl(Exec E) {
    AST arg = E.parse();
    if (arg instanceof ASTId) arg = Env.staticLookup((ASTId)arg);
    ASTTimeOp res = (ASTTimeOp) clone();
    res._asts = new AST[]{arg};
    return res;
  }

  abstract long op( MutableDateTime dt );

  @Override void apply(Env env) {
    // Single instance of MDT for the single call
    if( !env.isAry() ) {        // Single point
      double d = env.peekDbl();
      if( !Double.isNaN(d) ) d = op(new MutableDateTime((long)d));
      env.poppush(1, new ValNum(d));
      return;
    }
    // Whole column call
    Frame fr = env.peekAry();
    final ASTTimeOp uni = this;
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        MutableDateTime dt = new MutableDateTime(0);
        for( int i=0; i<nchks.length; i++ ) {
          NewChunk n =nchks[i];
          Chunk c = chks[i];
          int rlen = c._len;
          for( int r=0; r<rlen; r++ ) {
            double d = c.atd(r);
            if( !Double.isNaN(d) ) {
              dt.setMillis((long)d);
              d = uni.op(dt);
            }
            n.addNum(d);
          }
        }
      }
    }.doAll(fr.numCols(),fr).outputFrame(fr._names, null);
   env.poppush(1, new ValFrame(fr2));
  }
}
//
class ASTYear  extends ASTTimeOp { @Override String opStr(){ return "year" ; } @Override ASTOp make() {return new ASTYear  ();} @Override long op(MutableDateTime dt) { return dt.getYear();}}
class ASTMonth extends ASTTimeOp { @Override String opStr(){ return "month"; } @Override ASTOp make() {return new ASTMonth ();} @Override long op(MutableDateTime dt) { return dt.getMonthOfYear()-1;}}
class ASTDay   extends ASTTimeOp { @Override String opStr(){ return "day"  ; } @Override ASTOp make() {return new ASTDay   ();} @Override long op(MutableDateTime dt) { return dt.getDayOfMonth();}}
class ASTHour  extends ASTTimeOp { @Override String opStr(){ return "hour" ; } @Override ASTOp make() {return new ASTHour  ();} @Override long op(MutableDateTime dt) { return dt.getHourOfDay();}}
class ASTMinute extends ASTTimeOp { @Override String opStr(){return "minute";} @Override ASTOp make() {return new ASTMinute();} @Override long op(MutableDateTime dt) { return dt.getMinuteOfHour();}}
class ASTSecond extends ASTTimeOp { @Override String opStr(){return "second";} @Override ASTOp make() {return new ASTSecond();} @Override long op(MutableDateTime dt) { return dt.getSecondOfMinute();}}
class ASTMillis extends ASTTimeOp { @Override String opStr(){return "millis";} @Override ASTOp make() {return new ASTMillis();} @Override long op(MutableDateTime dt) { return dt.getMillisOfSecond();}}
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
//    Frame fr2 = new MRTask() {
//      @Override public void map(Chunk chk, NewChunk nchk) {
//        int rstart = (int)(diffs*lag - chk._start);
//        if(rstart > chk._len) return;
//        rstart = Math.max(0, rstart);
//
//        // Formula: \Delta_h^n x_t = \sum_{i=0}^n (-1)^i*\binom{n}{k}*x_{t-i*h}
//        for(int r = rstart; r < chk._len; r++) {
//          double x = chk.atd(r);
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
    if (l instanceof ASTId) l = Env.staticLookup((ASTId)l);
    AST r = E.parse();
    if (r instanceof ASTId) r = Env.staticLookup((ASTId)r);
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
                if(chks[i].vec().isUUID() || (chks[i].isNA(ro) && !bin.opStr().equals("|"))) { n.addNum(Double.NaN); continue; }
                if (chks[i].vec().isEnum()) l = chks[i].vec().domain()[(int)chks[i].atd(ro)];
                else lv = chks[i].atd(ro);
              } else if (sf0 == null) {
                if (Double.isNaN(df0) && !bin.opStr().equals("|")) { n.addNum(Double.NaN); continue; }
                lv = df0; l = null;
              } else {
                l = sf0;
              }

              // Initialize the rhs value
              if (rf) {
                if(chks[i+(lf ? nchks.length:0)].vec().isUUID() || chks[i].isNA(ro) && !bin.opStr().equals("|")) { n.addNum(Double.NaN); continue; }
                if (chks[i].vec().isEnum()) r = chks[i].vec().domain()[(int)chks[i].atd(ro)];
                else rv = chks[i+(lf ? nchks.length:0)].atd(ro);
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
    env.poppush(2, new ValFrame(fr2));
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
class ASTMod extends ASTBinOp { public ASTMod() { super(); } @Override String opStr(){ return "mod"  ;} @Override ASTOp make() {return new ASTMod ();}
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
  protected static double _init;
  protected static boolean _narm;        // na.rm in R
  protected static int _argcnt;
  ASTReducerOp( double init) {
    super(new String[]{"","dblary","...", "na.rm"});
    _init = init;
  }

  ASTReducerOp parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    dblarys.add(ary);
    AST a;
    E.skipWS();
    while (true) {
      a = E.skipWS().parse();
      if (a instanceof ASTId) {
        AST ast = E._env.lookup((ASTId)a);
        if (ast instanceof ASTFrame || ast instanceof ASTRaft) {dblarys.add(a); continue; } else break;
      }
      if (a instanceof ASTNum || a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTBinOp || a instanceof ASTUniOp || a instanceof ASTReducerOp || a instanceof ASTRaft)
        dblarys.add(a);
      else break;
    }
    // Get the na.rm last
    try {
      a = E._env.lookup((ASTId) a);
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Expected the na.rm value to be one of $TRUE, $FALSE, $T, $F");
    }
    _narm = ((ASTNum)a).dbl() == 1;
    ASTReducerOp res = (ASTReducerOp) clone();
    AST[] arys = new AST[_argcnt = dblarys.size()];
    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
    res._asts = arys;
    return res;
  }

  @Override double[] map(Env env, double[] in, double[] out, AST[] args) {
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
        Frame fr = env.popAry(); // pop w/o lowering refcnts ... clean it up later
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        sum = op(sum,_narm?new NaRmRedOp(this).doAll(fr)._d:new RedOp(this).doAll(fr)._d);
      }
    env.push(new ValNum(sum));
  }

  @Override void exec(Env e, AST arg1, AST[] args) {
    if (args == null) {
      _init = 0;
      _narm = true;
      _argcnt = 1;
    }
    arg1.exec(e);
    e._global._frames.put(Key.make().toString(), e.peekAry());
    apply(e);
  }

  private static class RedOp extends MRTask<RedOp> {
    final ASTReducerOp _bin;
    RedOp( ASTReducerOp bin ) { _bin = bin; _d = ASTReducerOp._init; }
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        if (C.vec().isEnum() || C.vec().isUUID() || C.vec().isString()) continue; // skip enum/uuid vecs
        for (int r = 0; r < rows; r++)
          _d = _bin.op(_d, C.atd(r));
        if (Double.isNaN(_d)) break;
      }
    }
    @Override public void reduce( RedOp s ) { _d = _bin.op(_d,s._d); }
  }

  private static class NaRmRedOp extends MRTask<NaRmRedOp> {
    final ASTReducerOp _bin;
    NaRmRedOp( ASTReducerOp bin ) { _bin = bin; _d = ASTReducerOp._init; }
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        if (C.vec().isEnum() || C.vec().isUUID() || C.vec().isString()) continue; // skip enum/uuid vecs
        for (int r = 0; r < rows; r++)
          if (!Double.isNaN(C.atd(r)))
            _d = _bin.op(_d, C.atd(r));
        if (Double.isNaN(_d)) break;
      }
    }
    @Override public void reduce( NaRmRedOp s ) { _d = _bin.op(_d,s._d); }
  }
}

class ASTSum extends ASTReducerOp { ASTSum() {super(0);} @Override String opStr(){ return "sum";} @Override ASTOp make() {return new ASTSum();} @Override double op(double d0, double d1) { return d0+d1;}}

class ASTRbind extends ASTUniPrefixOp {
  protected static int argcnt;
  @Override String opStr() { return "rbind"; }
  public ASTRbind() { super(new String[]{"rbind", "ary","..."}); }
  @Override ASTOp make() { return new ASTRbind(); }
  ASTRbind parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    dblarys.add(ary);
    AST a;
    boolean broke = false;
    while (E.skipWS().hasNext()) {
      a = E.parse();
      if (a instanceof ASTId) {
        AST ast = E._env.lookup((ASTId)a);
        if (ast instanceof ASTFrame || ast instanceof ASTRaft) { dblarys.add(a); }
        else {broke = true; break; } // if not a frame then break here since we are done parsing Frame args
      }
      else if (a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTBinOp || a instanceof ASTUniOp || a instanceof ASTReducerOp || a instanceof ASTRaft) { // basically anything that returns a Frame...
        dblarys.add(a);
      }
      else { broke = true; break; }
    }
    if (broke) E.rewind();
    Collections.reverse(dblarys);
    ASTRbind res = (ASTRbind) clone();
    res._asts = dblarys.toArray(new AST[argcnt=dblarys.size()]);
    return res;
  }

  private String get_type(byte t) {
    switch(t) {
      case Vec.T_ENUM: return "factor";
      case Vec.T_NUM:  return "numeric";
      case Vec.T_STR:  return "String";
      case Vec.T_TIME: return "time";
      case Vec.T_UUID: return "UUID";
      default: return "bad";
    }
  }

  private static class RbindMRTask extends MRTask<RbindMRTask> {
    private final int[] _emap;
    private final int _chunkOffset;
    private final Vec _v;
    RbindMRTask(H2O.H2OCountedCompleter hc, int[] emap, Vec v, int offset) { super(hc); _emap = emap; _v = v; _chunkOffset = offset;}

    @Override public void map(Chunk cs) {
      int idx = _chunkOffset+cs.cidx();
      Key ckey = Vec.chunkKey(_v._key, idx);
      if (_emap != null) {
        assert !cs.hasFloat(): "Input chunk ("+cs.getClass()+") has float, but is expected to be enum";
        NewChunk nc = new NewChunk(_v, idx);
        // loop over rows and update ints for new domain mapping according to vecs[c].domain()
        for (int r=0;r < cs._len;++r) {
          if (cs.isNA(r)) nc.addNA();
          else nc.addNum(_emap[(int)cs.at8(r)], 0);
        }
        nc.close(_fs);
      } else {
        Chunk oc = (Chunk)cs.clone();
        oc.setStart(-1);
        oc.setVec(null);
        oc.setBytes(cs.getBytes().clone()); // needless replication of the data, can do ref counting on byte[] _mem
        DKV.put(ckey, oc, _fs, true);
      }
    }
  }

  private static class RbindTask extends H2O.H2OCountedCompleter<RbindTask> {
    final transient Vec[] _vecs;
    final Vec _v;
    final long[] _espc;
    String[] _dom;

    RbindTask(H2O.H2OCountedCompleter cc, Vec[] vecs, Vec v, long[] espc) { super(cc); _vecs = vecs; _v = v; _espc = espc; }

    private static Map<Integer, String> invert(Map<String, Integer> map) {
      Map<Integer, String> inv = new HashMap<>();
      for (Map.Entry<String, Integer> e : map.entrySet()) {
        inv.put(e.getValue(), e.getKey());
      }
      return inv;
    }

    @Override protected void compute2() {
      addToPendingCount(_vecs.length-1);
      boolean isEnum = _vecs[0].domain() != null;
      int[][] emaps  = new int[_vecs.length][];

      if (isEnum) {
        // loop to create BIG domain
        HashMap<String, Integer> dmap = new HashMap<>(); // probably should allocate something that's big enough (i.e. 2*biggest_domain)
        int c = 0;
        for (int i = 0; i < _vecs.length; ++i) {
          emaps[i] = new int[_vecs[i].domain().length];
          for (int j = 0; j < emaps[i].length; ++j)
            if (!dmap.containsKey(_vecs[i].domain()[j]))
              dmap.put(_vecs[i].domain()[j], emaps[i][j]=c++);
            else emaps[i][j] = dmap.get(_vecs[i].domain()[j]);
        }
        _dom = new String[dmap.size()];
        HashMap<Integer, String> inv = (HashMap<Integer, String>) invert(dmap);
        for (int s = 0; s < _dom.length; ++s) _dom[s] = inv.get(s);
      }
      int offset=0;
      for (int i=0; i<_vecs.length; ++i) {
        new RbindMRTask(this, emaps[i], _v, offset).asyncExec(_vecs[i]);
        offset += _vecs[i].nChunks();
      }
    }

    @Override public void onCompletion(CountedCompleter cc) {
        _v.setDomain(_dom);
        DKV.put(_v);
    }
  }

  private static class ParallelRbinds extends H2O.H2OCountedCompleter{

    private final Env _env;
    private final int _argcnt;
    private final AtomicInteger _ctr;
    private int _maxP = 100;

    private long[] _espc;
    private Vec[] _vecs;
    ParallelRbinds(Env e, int argcnt) { _env = e; _argcnt = argcnt; _ctr = new AtomicInteger(_maxP-1); }  //TODO pass maxP to constructor

    @Override protected void compute2() {
      addToPendingCount(_env.peekAry().numCols()-1);
      int nchks=0;
      for (int i =0; i < _argcnt; ++i)
        nchks+=_env.peekAryAt(-i).anyVec().nChunks();

      _espc = new long[nchks+1];
      int coffset = _env.peekAry().anyVec().nChunks();
      long[] first_espc = _env.peekAry().anyVec().get_espc();
      System.arraycopy(first_espc, 0, _espc, 0, first_espc.length);
      for (int i=1; i< _argcnt; ++i) {
        long roffset = _espc[coffset];
        long[] espc = _env.peekAryAt(-i).anyVec().get_espc();
        int j = 1;
        for (; j < espc.length; j++)
          _espc[coffset + j] = roffset+ espc[j];
        coffset += _env.peekAryAt(-i).anyVec().nChunks();
      }

      Key[] keys = _env.peekAry().anyVec().group().addVecs(_env.peekAry().numCols());
      _vecs = new Vec[keys.length];
      for (int i=0; i<_vecs.length; ++i)
        _vecs[i] = new Vec( keys[i], _espc, null, _env.peekAry().vec(i).get_type());

      for (int i=0; i < Math.min(_maxP, _vecs.length); ++i) forkVecTask(i);
    }

    private void forkVecTask(final int i) {
      Vec[] vecs = new Vec[_argcnt];
      for (int j= 0; j < _argcnt; ++j)
        vecs[j] = _env.peekAryAt(-j).vec(i);
      new RbindTask(new Callback(), vecs, _vecs[i], _espc).fork();
    }

    private class Callback extends H2O.H2OCallback {
      public Callback(){super(ParallelRbinds.this);}
      @Override public void callback(H2O.H2OCountedCompleter h2OCountedCompleter) {
        int i = _ctr.incrementAndGet();
        if(i < _vecs.length)
          forkVecTask(i);
      }
    }
  }

  @Override void apply(Env env) {
    // quick check to make sure rbind is feasible
    if (argcnt == 1) { return; } // leave stack as is

    Frame f1 = env.peekAry();
    // do error checking and compute new offsets in tandem
    for (int i = 1; i < argcnt; ++i) {
      Frame t = env.peekAryAt(-i);

      // check columns match
      if (t.numCols() != f1.numCols())
        throw new IllegalArgumentException("Column mismatch! Expected " + f1.numCols() + " but frame has " + t.numCols());

      // check column types
      for (int c = 0; c < f1.numCols(); ++c) {
        if (f1.vec(c).get_type() != t.vec(c).get_type())
          throw new IllegalArgumentException("Column type mismatch! Expected type " + get_type(f1.vec(c).get_type()) + " but vec has type " + get_type(t.vec(c).get_type()));
      }
    }
    ParallelRbinds t;
    H2O.submitTask(t =new ParallelRbinds(env, argcnt)).join();
    env.poppush(argcnt, new ValFrame(new Frame(f1.names(), t._vecs)));
  }
}

class ASTCbind extends ASTUniPrefixOp {
  protected static int argcnt;
  @Override String opStr() { return "cbind"; }
  public ASTCbind() { super(new String[]{"cbind","ary", "..."}); }
  @Override ASTOp make() {return new ASTCbind();}
  ASTCbind parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    dblarys.add(ary);
    AST a;
    boolean broke = false;
    while (E.skipWS().hasNext()) {
      a = E.parse();
      if (a instanceof ASTId) {
        AST ast = E._env.lookup((ASTId)a);
        if (ast instanceof ASTFrame || ast instanceof ASTRaft) { dblarys.add(a); }
        else {broke = true; break; } // if not a frame then break here since we are done parsing Frame args
      }
      else if (a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTBinOp || a instanceof ASTUniOp || a instanceof ASTReducerOp || a instanceof ASTRaft) { // basically anything that returns a Frame...
        dblarys.add(a);
      }
      else { broke = true; break; }
    }
    if (broke) E.rewind();
    ASTCbind res = (ASTCbind) clone();
    AST[] arys = new AST[argcnt=dblarys.size()];
    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
    res._asts = arys;
    return res;
  }
  @Override void apply(Env env) {
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
      Frame f = env.peekAryAt(i-argcnt+1);  // Reverse order off stack
      Frame ff = f.deepSlice(null,null);  // deep copy the frame, R semantics...
      Frame new_frame = fr.makeCompatible(ff);
      if (f.numCols() == 1) fr.add(f.names()[0], new_frame.anyVec());
      else fr.add(new_frame);
    }
    env.pop(argcnt);

    env.pushAry(fr);
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
        Frame fr = env.popAry();
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        for (Vec v : fr.vecs())
          if (v.naCnt() > 0 && !_narm) { min = Double.NaN; break; }
          else min = Math.min(min, v.min());
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
        Frame fr = env.popAry();
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        for (Vec v : fr.vecs())
          if (v.naCnt() > 0 && !_narm) { max = Double.NaN; break; }
          else max = Math.max(max, v.max());
      }
    env.push(new ValNum(max));
  }
}

// R like binary operator &&
class ASTAND extends ASTBinOp {
  @Override String opStr() { return "&&"; }
  ASTAND( ) { super();}
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
  protected static String _newname;
  @Override String opStr() { return "rename"; }
  ASTRename() { super(new String[] {"", "ary", "new_name"}); }
  @Override ASTOp make() { return new ASTRename(); }
  ASTRename parse_impl(Exec E) {
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    _newname = ((ASTString)E.parse())._s;
    ASTRename res = (ASTRename) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Frame fr = e.popAry();
    Frame fr2 = new Frame(Key.make(_newname), fr.names(), fr.deepSlice(null,null).vecs());
    DKV.put(Key.make(_newname), fr2);
    e.pushAry(fr2);  // the vecs have not changed and their refcnts remain the same
  }
}

class ASTMatch extends ASTUniPrefixOp {
  protected static double _nomatch;
  protected static String[] _matches;
  @Override String opStr() { return "match"; }
  ASTMatch() { super( new String[]{"", "ary", "table", "nomatch", "incomparables"}); }
  @Override ASTOp make() { return new ASTMatch(); }
  ASTMatch parse_impl(Exec E) {
    // First parse out the `ary` arg
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // The `table` arg
    if (!E.skipWS().hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
    _matches = E.peek() == '{' ? E.xpeek('{').parseString('}').split(";") : new String[]{E.parseString(E.peekPlus())};
    // cleanup _matches
    for (int i = 0; i < _matches.length; ++i) _matches[i] = _matches[i].replace("\"", "").replace("\'", "");
    // `nomatch` is just a number in case no match
    try {
      ASTNum nomatch = (ASTNum) E.skipWS().parse();
      _nomatch = nomatch.dbl();
    } catch(ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `nomatch` expected a number.");
    }
    // drop the incomparables arg for now ...
    AST incomp = E.skipWS().parse();
    ASTMatch res = (ASTMatch) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Frame fr = e.popAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("can only match on a single categorical column.");
    if (!fr.anyVec().isEnum()) throw new IllegalArgumentException("can only match on a single categorical column.");
    Key tmp = Key.make();
    final String[] matches = _matches;
    Frame rez = new MRTask() {
      private int in(String s) { return Arrays.asList(matches).contains(s) ? 1 : 0; }
      @Override public void map(Chunk c, NewChunk n) {
        int rows = c._len;
        for (int r = 0; r < rows; ++r) n.addNum(in(c.vec().domain()[(int)c.at8(r)]));
      }
    }.doAll(1, fr.anyVec()).outputFrame(tmp, null, null);
    e.pushAry(rez);
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
    // op1 is NaN ? push NaN
    if (Double.isNaN(op1)) {
      env.poppush(2, new ValNum(Double.NaN));
      return;
    }
    double op2 = !Double.isNaN(op1) && op1!=0 ? 1 : (env.isNum()) ? env.peekDbl()
                    : (env.isAry()) ? env.peekAry().vecs()[0].at(0) : Double.NaN;

    // op2 is NaN ? push NaN
    if (Double.isNaN(op2)) {
      env.poppush(2, new ValNum(op2));
      return;
    }

    // both 0 ? push False
    if (op1 == 0 && op2 == 0) {
      env.poppush(2, new ValNum(0.0));
      return;
    }

    // else push True
    env.poppush(2, new ValNum(1.0));
  }
}

// Similar to R's seq_len
class ASTSeqLen extends ASTUniPrefixOp {
  protected static double _length;
  @Override String opStr() { return "seq_len"; }
  ASTSeqLen( ) { super(new String[]{"seq_len", "n"}); }
  @Override ASTOp make() { return new ASTSeqLen(); }
  @Override ASTSeqLen parse_impl(Exec E) {
    try {
      _length = E.nextDbl();
    } catch(ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `n` expected to be a number.");
    }
    ASTSeqLen res = (ASTSeqLen) clone();
    res._asts = new AST[]{};
    return res;
  }

  @Override void apply(Env env) {
    int len = (int) Math.ceil(_length);
    if (len <= 0)
      throw new IllegalArgumentException("Error in seq_len(" +len+"): argument must be coercible to positive integer");
    Frame fr = new Frame(new String[]{"c"}, new Vec[]{Vec.makeSeq(len)});
    env.pushAry(fr);
  }
}

// Same logic as R's generic seq method
class ASTSeq extends ASTUniPrefixOp {
  protected static double _from;
  protected static double _to;
  protected static double _by;

  @Override String opStr() { return "seq"; }
  ASTSeq() { super(new String[]{"seq", "from", "to", "by"}); }
  @Override ASTOp make() { return new ASTSeq(); }
  @Override ASTSeq parse_impl(Exec E) {
    // *NOTE*: This function creates a frame, there is no input frame!

    // Get the from
    try {
      if (!E.skipWS().hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST. Missing `from` argument.");
      _from = E.nextDbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `from` expected to be a number.");
    }
    // Get the to
    try {
      if (!E.skipWS().hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST. Missing `to` argument.");
      _to = E.nextDbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `to` expected to be a number.");
    }
    // Get the by
    try {
      if (!E.skipWS().hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST. Missing `by` argument.");
      _by = E.nextDbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `by` expected to be a number.");
    }
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
        env.pushAry(fr);
      }
    }
  }
}

class ASTRepLen extends ASTUniPrefixOp {
  protected static double _length;
  @Override String opStr() { return "rep_len"; }
  public ASTRepLen() { super(new String[]{"rep_len", "x", "length.out"}); }
  @Override ASTOp make() { return new ASTRepLen(); }
  @Override void apply(Env env) {

    // two cases if x is a frame: x is a single vec, x is a list of vecs
    if (env.isAry()) {
      final Frame fr = env.popAry();
      if (fr.numCols() == 1) {

        // In this case, create a new vec of length _length using the elements of x
        Vec v = Vec.makeRepSeq((long)_length, fr.numRows());  // vec of "indices" corresponding to rows in x
        new MRTask() {
          @Override public void map(Chunk c) {
            for (int i = 0; i < c._len; ++i)
              c.set(i, fr.anyVec().at((long) c.atd(i)));
          }
        }.doAll(v);
        v.setDomain(fr.anyVec().domain());
        Frame f = new Frame(new String[]{"C1"}, new Vec[]{v});
        env.pushAry(f);

      } else {

        // In this case, create a new frame with numCols = _length using the vecs of fr
        // this is equivalent to what R does, but by additionally calling "as.data.frame"
        String[] col_names = new String[(int)_length];
        for (int i = 0; i < col_names.length; ++i) col_names[i] = "C" + (i+1);
        Frame f = new Frame(col_names, new Vec[(int)_length]);
        for (int i = 0; i < f.numCols(); ++i)
          f.add(Frame.defaultColName(f.numCols()), fr.vec( i % fr.numCols() ));
        env.pushAry(f);
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
        env.pushAry(fr);
      } else if (env.isNum()) {
        Frame fr = new Frame(new String[]{"C1"}, new Vec[]{Vec.makeCon(env.popDbl(), len)});
        env.pushAry(fr);
      } else throw new IllegalArgumentException("Unkown input. Type: "+env.peekType() + " Stack: " + env.toString());
    }
  }
}

// Compute exact quantiles given a set of cutoffs, using multipass binning algo.
class ASTQtile extends ASTUniPrefixOp {
  protected static boolean _narm = false;
  protected static int     _type = 7;
  protected static double[] _probs = null;  // if probs is null, pop the _probs frame etc.

  @Override String opStr() { return "quantile"; }

  public ASTQtile() { super(new String[]{"quantile","x","probs", "na.rm", "names", "type"});}
  @Override ASTQtile make() { return new ASTQtile(); }
  @Override ASTQtile parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
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
    if (seq != null)
      if (seq instanceof ASTId) seq = Env.staticLookup((ASTId)seq);
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    _narm = ((ASTNum)a).dbl() == 1;
    //Get the type
    try {
      _type = (int) ((ASTNum) E.skipWS().parse()).dbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `type` expected to be a number.");
    }
    // Finish the rest
    ASTQtile res = (ASTQtile) clone();
    res._asts = seq == null ? new AST[]{ary} : new AST[]{ary, seq}; // in reverse order so they appear correctly on the stack.
    return res;
  }


  @Override void apply(Env env) {
    final Frame probs = _probs == null ? env.popAry() : null;
    if (probs != null && probs.numCols() != 1) throw new IllegalArgumentException("Probs must be a single vector.");

    Frame x = env.popAry();
    if (x.numCols() != 1) throw new IllegalArgumentException("Must specify a single column in quantile. Got: "+ x.numCols() + " columns.");
    Vec xv  = x.anyVec();
    if ( xv.isEnum() ) {
      throw new  IllegalArgumentException("Quantile: column type cannot be Categorical.");
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

    final int MAX_ITERATIONS = 16;
    final int MAX_QBINS = 1000; // less uses less memory, can take more passes
    final boolean MULTIPASS = true; // approx in 1 pass if false
    // Type 7 matches R default
    final int INTERPOLATION = _type; // 7 uses linear if quantile not exact on row. 2 uses mean.

    // a little obtuse because reusing first pass object, if p has multiple thresholds
    // since it's always the same (always had same valStart/End seed = vec min/max
    // some MULTIPASS conditionals needed if we were going to make this work for approx or exact
    final Quantiles[] qbins1 = new Quantiles.BinningTask(MAX_QBINS, xv.min(), xv.max()).doAll(xv)._qbins;
    for( int i=0; i<p.length; i++ ) {
      double quantile = p[i];
      // need to pass a different threshold now for each finishUp!
      qbins1[0].finishUp(xv, new double[]{quantile}, INTERPOLATION, MULTIPASS);
      if( qbins1[0]._done ) {
        res.set(i,qbins1[0]._pctile[0]);
      } else {
        // the 2-N map/reduces are here (with new start/ends. MULTIPASS is implied
        Quantiles[] qbinsM = new Quantiles.BinningTask(MAX_QBINS, qbins1[0]._newValStart, qbins1[0]._newValEnd).doAll(xv)._qbins;
        for( int iteration = 2; iteration <= MAX_ITERATIONS; iteration++ ) {
          qbinsM[0].finishUp(xv, new double[]{quantile}, INTERPOLATION, MULTIPASS);
          if( qbinsM[0]._done ) {
            res.set(i,qbinsM[0]._pctile[0]);
            break;
          }
          // the 2-N map/reduces are here (with new start/ends. MULTIPASS is implied
          qbinsM = new Quantiles.BinningTask(MAX_QBINS, qbinsM[0]._newValStart, qbinsM[0]._newValEnd).doAll(xv)._qbins;
        }
      }
    }
    res.chunkForChunkIdx(0).close(0,null);
    res.postWrite(new Futures()).blockForPending();
    Frame fr = new Frame(new String[]{"Quantiles"}, new Vec[]{res});
    env.pushAry(fr);
  }
}

class ASTSetColNames extends ASTUniPrefixOp {
  protected static long[] _idxs;
  protected static String[] _names;
  @Override String opStr() { return "colnames="; }
  public ASTSetColNames() { super(new String[]{}); }
  @Override ASTSetColNames make() { return new ASTSetColNames(); }

  // AST: (colnames<- $ary {indices} {names})
  // example:  (colnames<- $iris {#3;#5} {new_name1;new_name2})
  // also acceptable: (colnames<- $iris (: #3 #5) {new_name1;new_name2})
  @Override ASTSetColNames parse_impl(Exec E) {
    // frame we're changing column names of
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // col ids: can be a {#;#;#} or (: # #)
    AST a = E.skipWS().parse();
    if (a instanceof ASTSpan || a instanceof ASTSeries) {
      _idxs = (a instanceof ASTSpan) ? ((ASTSpan) a).toArray() : ((ASTSeries) a).toArray();
      Arrays.sort(_idxs);
    } else if (a instanceof ASTNum) {
      _idxs = new long[]{(long)((ASTNum) a).dbl()};
    } else throw new IllegalArgumentException("Bad AST: Expected a span, series, or number for the column indices.");

    // col names must either be an ASTSeries or a single string
    _names = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').replaceAll("\"","").split(";") : new String[]{E.parseString(E.peekPlus())};
    if (_names.length != _idxs.length)
      throw new IllegalArgumentException("Mismatch! Number of columns to change ("+(_idxs.length)+") does not match number of names given ("+(_names.length)+").");

    ASTSetColNames res = (ASTSetColNames)clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env env) {
    Frame f = env.popAry();
    for (int i=0; i < _names.length; ++i)
      f._names[(int)_idxs[i]] = _names[i];
    env.pushAry(f);
  }
}

class ASTRunif extends ASTUniPrefixOp {
  protected static long   _seed;
  @Override String opStr() { return "h2o.runif"; }
  public ASTRunif() { super(new String[]{"h2o.runif","dbls","seed"}); }
  @Override ASTOp make() {return new ASTRunif();}
  @Override ASTRunif parse_impl(Exec E) {
    // peel off the ary
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // parse the seed
    try {
      _seed = (long) E.nextDbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `seed` expected to be a number.");
    }
    ASTRunif res = (ASTRunif) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env env) {
    final long seed = _seed == -1 ? (new Random().nextLong()) : _seed;
    Vec rnd = env.popAry().anyVec().makeRand(seed);
    Frame f = new Frame(new String[]{"rnd"}, new Vec[]{rnd});
    env.pushAry(f);
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
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    _narm = ((ASTNum)a).dbl() == 1;
    ASTSdev res = (ASTSdev) clone();
    res._asts = new AST[]{ary}; // in reverse order so they appear correctly on the stack.
    return res;
  }
  @Override void apply(Env env) {
    if (env.isNum()) {
      env.poppush(1, new ValNum(Double.NaN));
    } else {
      Frame fr = env.peekAry();
      if (fr.vecs().length > 1)
        throw new IllegalArgumentException("sd does not apply to multiple cols.");
      if (fr.vecs()[0].isEnum())
        throw new IllegalArgumentException("sd only applies to numeric vector.");

      double sig = Math.sqrt(ASTVar.getVar(fr.anyVec(), _narm));
      env.poppush(1, new ValNum(sig));
    }
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
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // Get the trim
    AST y = E.skipWS().parse();
    if (y instanceof ASTString && ((ASTString)y)._s.equals("null")) {_ynull = true; y = ary; }
    else if (y instanceof ASTId) y = Env.staticLookup((ASTId)y);
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    try {
      _narm = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `na.rm` expected to be a number.");
    }
    // Get the `use`
    ASTString use;
    try {
      use = (ASTString) E.skipWS().parse();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `use` expected to be a string.");
    }
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

      if (fr.numRows() == 1) {
        double xmean=0; double ymean=0; double divideby = fr.numCols()-1; double ss=0;
        for (Vec v : fr.vecs()) xmean+= v.at(0);
        for (Vec v : y.vecs())  ymean+= v.at(0);
        xmean /= (divideby+1); ymean /= (divideby+1);

        if (Double.isNaN(xmean) || Double.isNaN(ymean)) { ss = Double.NaN; }
        else {
          for (int r = 0; r <= divideby; ++r) {
            ss += (fr.vecs()[r].at(0) - xmean) * (y.vecs()[r].at(0) - ymean);
          }
        }
        env.poppush(3, new ValNum(ss == Double.NaN ? ss : ss/divideby));

      } else {

        final double[/*cols*/][/*rows*/] covars = new double[y.numCols()][fr.numCols()];
        final CovarTask tsks[][] = new CovarTask[y.numCols()][fr.numCols()];
        final Frame frs[][] = new Frame[y.numCols()][fr.numCols()];
        final double xmeans[] = new double[fr.numCols()];
        final double ymeans[] = new double[y.numCols()];
        for (int r = 0; r < fr.numCols(); ++r) xmeans[r] = getMean(fr.vecs()[r], _narm, use);
        for (int c = 0; c < y.numCols(); ++c) ymeans[c] = getMean(y.vecs()[c], _narm, use);
        for (int c = 0; c < y.numCols(); ++c) {
          for (int r = 0; r < fr.numCols(); ++r) {
            frs[c][r] = new Frame(y.vecs()[c], fr.vecs()[r]);
            tsks[c][r] = new CovarTask(ymeans[c], xmeans[r]).doAll(frs[c][r]);
          }
        }
        for (int c = 0; c < y.numCols(); c++)
          for (int r = 0; r < fr.numCols(); r++) {
            covars[c][r] = tsks[c][r].getResult()._ss / (fr.numRows() - 1);
            frs[c][r] = null;
          }

        // Just push the scalar if input is a single col
        if (covars.length == 1 && covars[0].length == 1) env.poppush(3, new ValNum(covars[0][0]));
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
          env.poppush(3, new ValFrame(new Frame(colnames, vecs)));
        }
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
        _ss += (x.atd(r) - _xmean) * (y.atd(r) - _ymean);
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
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // Get the trim
    try {
      _trim = ((ASTNum) (E.skipWS().parse())).dbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `trim` expected to be a number.");
    }
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.skipWS().parse());
    try {
      _narm = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `na.rm` expected to be a number.");
    }
    // Finish the rest
    ASTMean res = (ASTMean) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void exec(Env e, AST arg1, AST[] args) {
    arg1.exec(e);
    e._global._frames.put(Key.make().toString(), e.peekAry());
    if (args != null) {
      if (args.length > 2) throw new IllegalArgumentException("Too many arguments passed to `mean`");
      for (AST a : args) {
        if (a instanceof ASTId) {
          _narm = ((ASTNum) e.lookup((ASTId) a)).dbl() == 1;
        } else if (a instanceof ASTNum) {
          _trim = ((ASTNum) a).dbl();
        }
      }
    }
    apply(e);
  }

  @Override void apply(Env env) {
    if (env.isNum()) return;
    Frame fr = env.popAry(); // get the frame w/o sub-reffing
    if (fr.numCols() > 1 && fr.numRows() > 1)
      throw new IllegalArgumentException("mean does not apply to multiple cols.");
    for (Vec v : fr.vecs()) if (v.isEnum())
      throw new IllegalArgumentException("mean only applies to numeric vector.");
    if (fr.numCols() > 1) {
      double mean=0;
      for(Vec v : fr.vecs()) mean += v.at(0);
      env.push(new ValNum(mean/fr.numCols()));
    } else {
      MeanNARMTask t = new MeanNARMTask(_narm).doAll(fr.anyVec()).getResult();
      if (t._rowcnt == 0 || Double.isNaN(t._sum)) {
        double ave = Double.NaN;
        env.push(new ValNum(ave));
      } else {
        double ave = t._sum / t._rowcnt;
        env.push(new ValNum(ave));
      }
    }
  }

  @Override double[] map(Env e, double[] in, double[] out, AST[] args) {
    if (args != null) {
      if (args.length > 2) throw new IllegalArgumentException("Too many arguments passed to `mean`");
      for (AST a : args) {
        if (a instanceof ASTId) {
          _narm = ((ASTNum) e.lookup((ASTId) a)).dbl() == 1;
        } else if (a instanceof ASTNum) {
          _trim = ((ASTNum) a).dbl();
        }
      }
    }
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
          if (!c.isNA(r)) { _sum += c.atd(r); _rowcnt++;}
      } else {
        for (int r = 0; r < c._len; r++)
          if (c.isNA(r)) { _rowcnt = 0; _sum = Double.NaN; return; } else { _sum += c.atd(r); _rowcnt++; }
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
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    AST two = E.skipWS().parse();
    if (two instanceof ASTString) two = new ASTNull();
    if (two instanceof ASTId) two = Env.staticLookup((ASTId)two);
    ASTTable res = (ASTTable)clone();
    res._asts = new AST[]{ary, two}; //two is pushed on, then ary is pushed on
    return res;
  }

  @Override void apply(Env env) {
    Frame two = env.peekType() == Env.NULL ? null : env.popAry();
    if (two == null) env.pop();
    Frame one = env.popAry();

    // Rules: two != null => two.numCols == one.numCols == 1
    //        two == null => one.numCols == 1 || one.numCols == 2
    // Anything else is IAE

    if (two != null)
      if (two.numCols() != 1 || one.numCols() != 1)
        throw new IllegalArgumentException("`table` supports at *most* two vectors");
    else
      if (one.numCols() < 1 || one.numCols() > 2 )
        throw new IllegalArgumentException("`table` supports at *most* two vectors and at least one vector.");

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
    Futures fs = new Futures();
    vecs[0] = v0.close(fs);
    colnames[0] = "row.names";
    if (ncol==1) colnames[1] = "Count";
    for (int level1=0; level1 < counts.length; level1++) {
      AppendableVec v = new AppendableVec(keys[level1+1]);
      NewChunk c = new NewChunk(v,0);
      v.setDomain(null);
      for (int level0=0; level0 < counts[level1].length; level0++)
        c.addNum((double) counts[level1][level0]);
      c.close(0, null);
      vecs[level1+1] = v.close(fs);
      if (ncol>1) {
        colnames[level1+1] = domains[1]==null? Long.toString(levels[1][level1]) : domains[1][(int)(levels[1][level1])];
      }
    }
    fs.blockForPending();
    Frame fr2 = new Frame(colnames, vecs);
    env.pushAry(fr2);
  }

  protected static class Tabularize extends MRTask<Tabularize> {
    public final long[][]  _domains;
    public long[][] _counts;

    public Tabularize(long[][] dom) { super(); _domains=dom; }
    @Override public void map(Chunk[] cs) {
      assert cs.length == _domains.length;
      _counts = _domains.length==1? new long[1][] : new long[_domains[1].length][];
      for (int i=0; i < _counts.length; i++) _counts[i] = new long[_domains[0].length];
      for (int i=0; i < cs[0]._len; i++) {
        if (cs[0].isNA(i)) continue;
        long ds[] = _domains[0];
        int level0 = Arrays.binarySearch(ds,cs[0].at8(i));
        assert 0 <= level0 && level0 < ds.length : "l0="+level0+", len0="+ds.length+", min="+ds[0]+", max="+ds[ds.length-1];
        int level1;
        if (cs.length>1) {
          if (cs[1].isNA(i)) continue; else level1 = Arrays.binarySearch(_domains[1],(int)cs[1].at8(i));
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

// Conditional merge of two Frames/Vecs
// Result is always the height as "tst"
// That means we support R semantics here and do the replication of true/false as needed.
// We also do the same thing R does with factor levels -> replace with their int value...
// What we do NOT support is the case when true.numCols() != false.numCols()
// The pseudo-code is: [t <- true; t[!tst] < false[!tst]; t]

// Cases to consider: (2^2 possible input types f: Frame, v: Vec (1D frame), the tst may only be a single column bit vec.
//
//       tst | true | false
//       ----|------|------
//     1.  v     f      f
//     2.  v     f      v
//     3.  v     v      f
//     4.  v     v      v
//
//  Additionally, how to cut/expand frames `true` and `false` to match `tst`.

class ASTIfElse extends ASTUniPrefixOp {
  static final String VARS[] = new String[]{"ifelse","tst","true","false"};

  ASTIfElse( ) { super(VARS); }
  @Override ASTOp make() {return new ASTIfElse();}
  @Override String opStr() { return "ifelse"; }
  @Override ASTIfElse parse_impl(Exec E) {
    AST tst = E.parse();
    if (tst instanceof ASTId) tst = Env.staticLookup((ASTId) tst);

    // still have an instance of ASTId, and lookup gives 0 (%FALSE) or 1 (%TRUE)
    if (tst instanceof ASTId) {
      try {
        double d = ((ASTNum) E._env.lookup((ASTId)tst))._d;
        if (d == 0 || d == 1) {  // FALSE or TRUE
          tst = new ASTFrame(new Frame(Key.make(), null, new Vec[]{Vec.makeCon(d, 1)} ) );
        }
      } catch (ClassCastException e) {
        throw new IllegalArgumentException("`test` must be a frame or TRUE/FALSE");
      }
    }
    AST yes = E.skipWS().parse(); // could be num
    if (yes instanceof ASTId) yes = Env.staticLookup((ASTId)yes);
    AST no  = E.skipWS().parse(); // could be num
    if (no instanceof ASTId) no = Env.staticLookup((ASTId)no);
    ASTIfElse res = (ASTIfElse)clone();
    res._asts = new AST[]{no,yes,tst};
    return res;
  }

  // return frame compatible to tgt
  private Frame adaptToTst(Frame src, Frame tgt) {
    Key k = src._key == null ? Key.make() : src._key;
    // need to pute src in DKV if not in there
    if (src._key == null || DKV.get(src._key) == null)
      DKV.put(k, new Frame(k,src.names(),src.vecs()));

    // extend src
    StringBuilder sb=null;
    if (src.numRows() < tgt.numRows()) {
      // rbind the needed rows
      int nrbins = 1 + (int)((tgt.numRows() - src.numRows()) / src.numRows());
      long remainder = tgt.numRows() % src.numRows();
      sb = new StringBuilder("(rbind ");
      for (int i = 0; i < nrbins; ++i) sb.append("%").append(k).append((i == (nrbins - 1) && remainder<0) ? "" : " ");
      sb.append(remainder > 0 ? "([ %"+k+" (: #0 #"+(remainder-1)+") \"null\"))" : ")");
      Log.info("extending frame:" + sb.toString());

    // reduce src
    } else if (src.numRows() > tgt.numRows()) {
      long rmax = tgt.numRows() - 1;
      sb = new StringBuilder("([ %"+k+" (: #0 #"+rmax+"))");
    }

    if (sb != null) {
      Env env=null;
      Frame res;
      try {
        env = Exec.exec(sb.toString());
        res = env.popAry();
        res.unlock_all();
      } catch (Exception e) {
        throw H2O.fail();
      } finally {
        if (env!=null)env.unlock();
      }
      Frame ret = tgt.makeCompatible(res);
//      if (env != null) env.cleanup(ret==res?null:res, (Frame)DKV.remove(k).get());
      return ret;
    }
    src = DKV.remove(k).get();
    Frame ret = tgt.makeCompatible(src);
    if (src != ret) src.delete();
    return ret;
  }

  private Frame adaptToTst(double d, Frame tgt) {
    Frame v = new Frame(Vec.makeCon(d, tgt.numRows()));
    Frame ret = tgt.makeCompatible(v);
    if (ret != v) v.delete();
    return ret;
  }

  @Override void apply(Env env) {
    if (!env.isAry()) throw new IllegalArgumentException("`test` argument must be a frame: ifelse(`test`, `yes`, `no`)");
    Frame tst = env.popAry();
    if (tst.numCols() != 1)
      throw new IllegalArgumentException("`test` has "+tst.numCols()+" columns. `test` must have exactly 1 column.");
    Frame yes=null; double dyes=0;
    Frame no=null; double dno=0;
    if (env.isAry()) yes = env.popAry(); else dyes = env.popDbl();
    if (env.isAry()) no  = env.popAry(); else dno  = env.popDbl();

    if (yes != null && no != null) {
      if (yes.numCols() != no.numCols())
        throw new IllegalArgumentException("Column mismatch between `yes` and `no`. `yes` has" + yes.numCols() + "; `no` has " + no.numCols() + ".");
    } else if (yes != null) {
      if (yes.numCols() != 1)
        throw new IllegalArgumentException("Column mismatch between `yes` and `no`. `yes` has" + yes.numCols() + "; `no` has " + 1 + ".");
    } else if (no != null) {
      if (no.numCols() != 1)
        throw new IllegalArgumentException("Column mismatch between `yes` and `no`. `yes` has" + 1 + "; `no` has " + no.numCols() + ".");
    }

    Frame a_yes = yes == null ? adaptToTst(dyes, tst) : adaptToTst(yes,tst);
    Frame a_no  = no == null ? adaptToTst(dno, tst) : adaptToTst(no, tst);
    Frame frtst = (new Frame(tst)).add(a_yes).add(a_no);
    final int ycols = a_yes.numCols();

    // Run a selection picking true/false across the frame
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        int rows = chks[0]._len;
        int cols = chks.length;
        Chunk pred = chks[0];
        for (int r=0;r < rows;++r) {
          for (int c = (pred.atd(r) != 0 ? 1 : ycols + 1), col = 0; c < (pred.atd(r) != 0 ?ycols+1:cols); ++c) {
            if (chks[c].vec().isUUID())
              nchks[col++].addUUID(chks[c], r);
            else if (chks[c].vec().isString())
              nchks[col++].addStr(chks[c].atStr(new ValueString(), r));
            else
              nchks[col++].addNum(chks[c].atd(r));
          }
        }
      }
    }.doAll(yes==null?1:yes.numCols(),frtst).outputFrame(yes==null?(new String[]{"C1"}):yes.names(),null/*same as R: no domains*/);
    env.pushAry(fr2);
  }
}

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
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    // breaks first
    String[] cuts;
    try {
      cuts = E.skipWS().peek() == '{'
              ? E.xpeek('{').parseString('}').split(";")
              : E.peek() == '#' ? new String[]{Double.toString(((ASTNum) E.parse()).dbl())}
              : new String[]{E.parseString(E.peekPlus())};
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `breaks` was malformed. Bad AST input.");
    }
    for (int i = 0; i < cuts.length; ++i) cuts[i] = cuts[i].replace("\"", "").replace("\'", "");
    _cuts = new double[cuts.length];
    for (int i = 0; i < cuts.length; ++i) _cuts[i] = Double.valueOf(cuts[i]);
    // labels second
    try {
      _labels = E.skipWS().peek() == '{' ? E.xpeek('{').parseString('}').split(";") : new String[]{E.parseString(E.peekPlus())};
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `labels` was malformed. Bad AST input.");
    }
    // cleanup _labels
    for (int i = 0; i < _labels.length; ++i) _labels[i] = _labels[i].replace("\"", "").replace("\'", "");
    if (_labels.length==1 && _labels[0].equals("null")) _labels = null;
    AST inc_lowest = E.skipWS().parse();
    inc_lowest = E._env.lookup((ASTId)inc_lowest);
    try {
      _includelowest = ((ASTNum) inc_lowest).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `include.lowest` expected to be TRUE/FALSE.");
    }
    AST right = E.skipWS().parse();
    right = E._env.lookup((ASTId)right);
    try {
      _right = ((ASTNum) right).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `right` expected to be a TRUE/FALSE.");
    }
    ASTNum diglab;
    try {
      diglab = (ASTNum) E.skipWS().parse();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `dig.lab` expected to be a number.");
    }
    _diglab = diglab.dbl();
    _diglab = _diglab >= 12 ? 12 : _diglab; // cap at 12 digits
    ASTCut res = (ASTCut) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  private String left() { return _right ? "(" : "["; }
  private String rite() { return _right ? "]" : ")"; }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
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
          double x = c.atd(r);
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
    env.pushAry(fr2);
  }
}

class ASTFactor extends ASTUniPrefixOp {
  ASTFactor() { super(new String[]{"", "ary"});}
  @Override String opStr() { return "as.factor"; }
  @Override ASTOp make() {return new ASTFactor();}
  ASTFactor parse_impl(Exec E) {
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    ASTFactor res = (ASTFactor) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame ary = env.popAry();
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("factor requires a single column");
    Vec v0 = ary.anyVec();
    Vec v1 = v0.isEnum() ? null : v0.toEnum(); // toEnum() creates a new vec --> must be cleaned up!
    Frame fr = new Frame(ary._names, new Vec[]{v1 == null ? v0.makeCopy() : v1});
    env.pushAry(fr);
  }
}

class ASTCharacter extends ASTUniPrefixOp {
  ASTCharacter() { super(new String[]{"", "ary"});}
  @Override String opStr() { return "as.character"; }
  @Override ASTOp make() {return new ASTFactor();}
  ASTCharacter parse_impl(Exec E) {
    AST ary = E.parse();
    if (ary instanceof ASTId) ary = Env.staticLookup((ASTId)ary);
    ASTCharacter res = (ASTCharacter) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame ary = env.popAry();
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("character requires a single column");
    Vec v0 = ary.anyVec();
    Vec v1 = v0.isString() ? null : v0.toStringVec(); // toEnum() creates a new vec --> must be cleaned up!
    Frame fr = new Frame(ary._names, new Vec[]{v1 == null ? v0.makeCopy() : v1});
    env.pushAry(fr);
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
    for( Key key : KeySnapshot.globalSnapshot().keys()) {
      keys.addEnum(r++);
      domain.add(key.toString());
    }
    keys.close(fs);
    Vec c0 = av.close(fs);   // c0 is the row index vec
    fs.blockForPending();
    String[] key_domain = new String[domain.size()];
    for (int i = 0; i < key_domain.length; ++i) key_domain[i] = domain.get(i);
    c0.setDomain(key_domain);
    env.pushAry(new Frame(Key.make("h2o_ls"), new String[]{"key"}, new Vec[]{c0}));
  }

  private double getSize(Key k) {
    return (double)(((Frame) k.get()).byteSize());
//    if (k.isChunkKey()) return (double)((Chunk)DKV.get(k).get()).byteSize();
//    if (k.isVec()) return (double)((Vec)DKV.get(k).get()).rollupStats()._size;
//    return Double.NaN;
  }
}

// Variable length; flatten all the component arys
class ASTCat extends ASTUniPrefixOp {
  // form of this is (c ASTSpan)
  @Override String opStr() { return "c"; }
  public ASTCat( ) { super(new String[]{"cat","dbls", "..."});}
  @Override ASTOp make() {return new ASTCat();}
  @Override ASTCat parse_impl(Exec E) {
    ASTSeries a;
    try {
      if (!E.hasNext()) throw new IllegalArgumentException("End of input unexpected. Badly formed AST.");
      a = (ASTSeries) E.parse();
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("Expected ASTSeries object. Badly formed AST.");
    }

    ASTCat res = (ASTCat) clone();
    res._asts = new AST[]{a};
    return res;
  }

  @Override void apply(Env env) {
    final ValSeries s = (ValSeries) env.pop();
    int id_span =0;
    long len = s._idxs.length;
    if (s._spans != null) {
      for (ASTSpan as : s._spans) len += (as._max - as._min + 1);
    }
    // now make an mapping of ValSeries -> Vec indices
    ArrayList<Long> idxs = new ArrayList<>();
    ArrayList<ASTSpan> spans = new ArrayList<>();
    long cur_id=0;
    for (int o : s._order) {
      if (o == 0) { // span
        assert s._spans != null;
        long id_min = cur_id;
        long id_max = cur_id + s._spans[id_span]._max - s._spans[id_span]._min;
        cur_id+=(s._spans[id_span]._max-s._spans[id_span++]._min+1);
        spans.add(new ASTSpan(id_min, id_max));
      } else {      // idx
        idxs.add(cur_id++);
      }
    }
    long[] idxsl = new long[idxs.size()];
    for (int i =0; i < idxsl.length; ++ i) idxsl[i] = idxs.get(i);
    final ValSeries ids = new ValSeries(idxsl, spans.toArray(new ASTSpan[spans.size()]));
    ids._order = s._order;

    Frame fr = new MRTask() {
      @Override public void map(Chunk[] cs) {
        Chunk c = cs[0];
        for (int r = 0; r < c._len; ++r) {
          long cur = c.start() + r;
          c.set(r, maprow(cur, ids, s));
        }
      }
    }.doAll(Vec.makeZero(len))._fr;

    env.pushAry(fr);
  }

  private long maprow(long cur, ValSeries ids, ValSeries s) {
    // get the location of the id in ids. This maps to the value in s.
    int span_idx = -1;
    int idxs_idx = 0;
    long at_value = -1;
    for (int o : ids._order) {
      if (o == 0) {  // span
        if (ids._spans[++span_idx].contains(cur)) {
          at_value = cur - ids._spans[span_idx]._min;
          break;
        }
      } else {
        boolean _br = false;
        if (idxs_idx >= 0) {
          for (int i = 0; i < ids._idxs.length; ++i) {
            if (ids._idxs[i] == cur) {
              at_value = i;
              _br = true;
              break;
            }
          }
          if (_br) break; else --idxs_idx;
        }
      }
    }
    if (span_idx >= 0) {
      return s._spans[span_idx]._min + at_value;
    } else return s._idxs[(int)at_value];
  }
}

// WIP

class ASTXorSum extends ASTReducerOp {
  ASTXorSum() {super(0); }
  @Override String opStr(){ return "xorsum";}
  @Override ASTOp make() {return new ASTXorSum();}
  @Override double op(double d0, double d1) {
    long d0Bits = Double.doubleToLongBits(d0);
    long d1Bits = Double.doubleToLongBits(d1);
    long xorsumBits = d0Bits ^ d1Bits;
    // just need to not get inf or nan. If we zero the upper 4 bits, we won't
    final long ZERO_SOME_SIGN_EXP = 0x0fffffffffffffffL;
    xorsumBits = xorsumBits & ZERO_SOME_SIGN_EXP;
    return Double.longBitsToDouble(xorsumBits);
  }
  @Override double[] map(Env env, double[] in, double[] out, AST[] args) {
    if (out == null || out.length < 1) out = new double[1];
    long xorsumBits = 0;
    long vBits;
    // for dp ieee 754 , sign and exp are the high 12 bits
    // We don't want infinity or nan, because h2o will return a string.
    double xorsum = 0;
    for (double v : in) {
      vBits = Double.doubleToLongBits(v);
      xorsumBits = xorsumBits ^ vBits;
    }
    // just need to not get inf or nan. If we zero the upper 4 bits, we won't
    final long ZERO_SOME_SIGN_EXP = 0x0fffffffffffffffL;
    xorsumBits = xorsumBits & ZERO_SOME_SIGN_EXP;
    xorsum = Double.longBitsToDouble(xorsumBits);
    out[0] = xorsum;
    return out;
  }
}

class ASTMMult extends ASTOp {
  ASTMMult() { super(VARS2);}

  ASTMMult parse_impl(Exec E) {
    AST l = E.parse();
    if (l instanceof ASTId) l = Env.staticLookup((ASTId)l);
    AST r = E.parse();
    if (r instanceof ASTId) r = Env.staticLookup((ASTId)r);
    ASTMMult res = new ASTMMult();
    res._asts = new AST[]{l,r};
    return res;
  }
  @Override
  String opStr() { return "x";}

  @Override
  ASTOp make() { return new ASTMMult();}

  @Override
  void apply(Env env) {
    env.poppush(2, new ValFrame(DMatrix.mmul(env.peekAryAt(-0), env.peekAryAt(-1))));
  }
}

class ASTTranspose extends ASTOp {
  ASTTranspose() { super(VARS1);}

  ASTTranspose parse_impl(Exec E) {
    AST arg = E.parse();
    if (arg instanceof ASTId) arg = Env.staticLookup((ASTId)arg);
    ASTTranspose res = new ASTTranspose();
    res._asts = new AST[]{arg};
    return res;
  }
  @Override
  String opStr() { return "t";}

  @Override
  ASTOp make() { return new ASTTranspose();}

  @Override
  void apply(Env env) {
    env.push(new ValFrame(DMatrix.transpose(env.popAry())));
  }
}


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
//      vec.domain = null;
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
//      Frame fr = env.popAry();
//      if(fr.numCols() != 1 || fr.vecs()[0].isEnum())
//        throw new IllegalArgumentException("Argument must be a single numeric column vector. Got an array with " + fr.numCols() + " columns. Column was an enum: " + fr.vecs()[0].isEnum());
//
//      Frame fr2 = new MRTask() {
//        @Override public void map(Chunk chk, NewChunk nchk) {
//          for(int r = 0; r < chk._len; r++) {
//            double x = chk.atd(r);
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
//      Frame fr2 = new MRTask() {
//        @Override public void map(Chunk chk, NewChunk nchk) {
//          for(int r = 0; r < chk._len; r++) {
//            double x = chk.atd(r);
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
