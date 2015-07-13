package water.rapids;

import hex.DMatrix;
import hex.quantile.Quantile;
import hex.quantile.QuantileModel;
import jsr166y.CountedCompleter;
import org.apache.commons.math3.special.Gamma;
import org.apache.commons.math3.util.FastMath;
import org.joda.time.DateTime;
import org.joda.time.DateTimeZone;
import org.joda.time.MutableDateTime;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import sun.misc.Unsafe;
import water.*;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.*;
import water.nbhm.NonBlockingHashMap;
import water.nbhm.UtilUnsafe;
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
    SYMBOLS.put("agg",new ASTGroupBy.AGG());
    SYMBOLS.put(")", new ASTNull());
    //lists
    SYMBOLS.put("list", new ASTAry());
    SYMBOLS.put("dlist", new ASTDoubleList());
    SYMBOLS.put("llist", new ASTLongList());
    SYMBOLS.put("flist", new ASTFloatList());
    SYMBOLS.put("slist", new ASTStringList());
    SYMBOLS.put("shortlist", new ASTShortList());
    SYMBOLS.put("ilist", new ASTIntList());
    SYMBOLS.put("blist", new ASTBoolList());
    SYMBOLS.put("clist", new ASTCharList());
    SYMBOLS.put("bytelist", new ASTByteList());

    //TODO: Have `R==` type methods (also `py==`, `js==`, etc.)

    // Unary infix ops
    putUniInfix(new ASTNot());
    // Binary infix ops
    putBinInfix(new ASTPlus());
    putBinInfix(new ASTSub());
    putBinInfix(new ASTMul());
    putBinInfix(new ASTMMult());
    putBinInfix(new ASTDiv());
    putBinInfix(new ASTIntDiv());
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
    putPrefix(new ASTAsNumeric());
    putPrefix(new ASTIsFactor());
    putPrefix(new ASTAnyFactor());              // For Runit testing
    putPrefix(new ASTCanBeCoercedToLogical());
    putPrefix(new ASTAnyNA());
    putPrefix(new ASTRound());
    putPrefix(new ASTSignif());
    putPrefix(new ASTTrun());
    putPrefix(new ASTLPut());
    putPrefix(new ASTGPut());
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
    putPrefix(new ASTMedian());
    putPrefix(new ASTMad());

    // Misc
    putPrefix(new ASTSetLevel());
    putPrefix(new ASTMatch ());
    putPrefix(new ASTRename());
    putPrefix(new ASTSeq   ());
    putPrefix(new ASTSeqLen());
    putPrefix(new ASTRepLen());
    putPrefix(new ASTQtile ());
    putPrefix(new ASTCbind ());
    putPrefix(new ASTRbind ());
    putPrefix(new ASTTable ());
//    putPrefix(new ASTReduce());
    putPrefix(new ASTIfElse());
    putPrefix(new ASTApply ());
    putPrefix(new ASTSApply());
    putPrefix(new ASTddply());
    putPrefix(new ASTMerge ());
    putPrefix(new ASTGroupBy());
    putPrefix(new ASTCumSum());
    putPrefix(new ASTCumProd());
    putPrefix(new ASTCumMin());
//    putPrefix(new ASTUnique());
    putPrefix(new ASTXorSum());
    putPrefix(new ASTRunif ());
    putPrefix(new ASTCut   ());
    putPrefix(new ASTLs    ());
    putPrefix(new ASTSetColNames());
    putPrefix(new ASTRemoveFrame());

    // Date
    putPrefix(new ASTasDate());
    putPrefix(new ASTToDate());

//Classes that may not come back:

//    putPrefix(new ASTfindInterval());
//    putPrefix(new ASTPrint ());
    putPrefix(new ASTCat   ());
//Time extractions, to and from msec since the Unix Epoch
    putPrefix(new ASTYear  ());
    putPrefix(new ASTMonth ());
    putPrefix(new ASTWeek  ());
    putPrefix(new ASTDay   ());
    putPrefix(new ASTDayOfWeek());
    putPrefix(new ASTHour  ());
    putPrefix(new ASTMinute());
    putPrefix(new ASTSecond());
    putPrefix(new ASTMillis());
    putPrefix(new ASTMktime());
    putPrefix(new ASTListTimeZones());
    putPrefix(new ASTGetTimeZone());
    putPrefix(new ASTFoldCombine());
    putPrefix(new ASTSetTimeZone());
    putPrefix(new COp());
    putPrefix(new ROp());
    putPrefix(new O());
    putPrefix(new ASTImpute());
    putPrefix(new ASTQPFPC());
    putPrefix(new ASTStoreSize());
    putPrefix(new ASTKeysLeaked());
    putPrefix(new ASTAll());
    putPrefix(new ASTNLevels());
    putPrefix(new ASTLevels());
    putPrefix(new ASTHist());
    // string mungers
    putPrefix(new ASTGSub());
    putPrefix(new ASTStrSplit());
    putPrefix(new ASTStrSub());
    putPrefix(new ASTToLower());
    putPrefix(new ASTToUpper());
    putPrefix(new ASTTrim());

    putPrefix(new ASTFilterNACols());
    putPrefix(new ASTSetDomain());
    putPrefix(new ASTRemoveVecs());

    putPrefix(new ASTKappa());
    putPrefix(new ASTWhich());
    putPrefix(new ASTWhichMax());
    putPrefix(new ASTMajorityVote());

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
  @Override void exec(Env e) { throw H2O.unimpl(); }
  // special exec for apply calls
  void exec(Env e, AST[] args) { throw H2O.unimpl("No exec method for `" + this.opStr() + "` during `apply` call"); }
  @Override int type() { return -1; }
  @Override String value() { throw H2O.unimpl(); }

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
    throw H2O.unimpl("Unimplemented: Could not find the operation or function " + op);
  }
}

abstract class ASTUniOrBinOp extends ASTOp {
  ASTUniOrBinOp(String[] vars) { super(vars); }
  double op( double d ) { throw H2O.unimpl(); }
  double op(double d0, double d1) { throw H2O.unimpl(); }
  String op( String s0, double d1 ) { throw H2O.unimpl(); }
  String op( double d0, String s1 ) { throw H2O.unimpl(); }
  String op( String s0, String s1 ) { throw H2O.unimpl(); }
}

abstract class ASTUniOp extends ASTUniOrBinOp {
  ASTUniOp() { super(VARS1); }
  protected ASTUniOp( String[] vars) { super(vars); }
  ASTUniOp parse_impl(Exec E) {
    AST arg = E.parse();
    E.eatEnd(); // eat ending ')'
    ASTUniOp res = (ASTUniOp) clone();
    res._asts = new AST[]{arg};
    return res;
  }

  @Override void exec(Env e, AST[] args) {
    args[0].exec(e);
    if( e.isAry() ) e.put(Key.make().toString(),e.peekAry());
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
    }.doAll(fr.numCols(),fr).outputFrame(fr._names, null);
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
    }.doAll(fr.numCols(),fr).outputFrame(fr._names, null);
    env.pushAry(fr2);
  }
}

class ASTFilterNACols extends ASTUniPrefixOp {
  double _frac;
  ASTFilterNACols() { super(); }
  @Override String opStr() { return "filterNACols"; }
  @Override ASTOp make() { return new ASTFilterNACols(); }
  ASTFilterNACols parse_impl(Exec E) {
    AST ary = E.parse();
    _frac = E.nextDbl();
    E.eatEnd();
    ASTFilterNACols res = (ASTFilterNACols)clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override public void apply(Env env) {
    Frame f = env.popAry();
    ArrayList<Integer> colsToKeep = new ArrayList<>();
    int i=0;
    double nrow = f.numRows();
    for( Vec v: f.vecs() ) {
      if ((v.naCnt() / nrow) < _frac)
        colsToKeep.add(i);
      i++;
    }

    Futures fs = new Futures();
    Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
    AppendableVec v = new AppendableVec(key);
    NewChunk chunk = new NewChunk(v, 0);
    for (Integer aColsToKeep : colsToKeep) chunk.addNum(aColsToKeep);
    chunk.close(0, fs);
    Vec vec = v.close(fs);
    fs.blockForPending();
    Frame fr2 = new Frame(vec);
    env.pushAry(fr2);
  }
}

class ASTasDate extends ASTUniPrefixOp {
  String _format;
  ASTasDate() { super(new String[]{"as.Date", "x", "format"}); }
  @Override String opStr() { return "as.Date"; }
  @Override ASTOp make() {return new ASTasDate();}
  @Override ASTasDate parse_impl(Exec E) {
    AST ast = E.parse();
    AST a = E.parse();
    if( a instanceof ASTString ) _format = ((ASTString)a)._s;
    else throw new IllegalArgumentException("`format` must be a string.");

    E.eatEnd(); // eat ending ')'
    ASTasDate res = (ASTasDate) clone();
    res._asts = new AST[]{ast};
    return res;
  }
  @Override void apply(Env env) {
    final String format = _format;
    if (format.isEmpty()) throw new IllegalArgumentException("as.Date requires a non-empty format string");
    // check the format string more?

    Frame fr = env.popAry();

    if( fr.vecs().length != 1 || !(fr.vecs()[0].isEnum() || fr.vecs()[0].isString()))
      throw new IllegalArgumentException("as.Date requires a single column of factors or strings");

    final String[] dom  = fr.anyVec().domain();
    final boolean isStr = dom==null && fr.anyVec().isString();
    if( !isStr )
      assert dom!=null : "as.Date error: domain is null, but vec is not String";

    Frame fr2 = new MRTask() {
      private transient DateTimeFormatter _fmt;
      @Override public void setupLocal() { _fmt=ParseTime.forStrptimePattern(format).withZone(ParseTime.getTimezone()); }
      @Override public void map( Chunk c, NewChunk nc ) {
        //done on each node in lieu of rewriting DateTimeFormatter as Iced
        String date;
        ValueString vStr = new ValueString();
        for( int i=0; i<c._len; ++i ) {
          if( !c.isNA(i) ) {
            if( isStr ) date = c.atStr(vStr, i).toString();
            else        date = dom[(int)c.at8(i)];
            nc.addNum(DateTime.parse(date,_fmt).getMillis(),0);
          } else nc.addNA();
        }
      }
    }.doAll(1,fr).outputFrame(fr._names, null);
    env.pushAry(fr2);
  }
}

// pass thru directly to Joda -- as.Date is because R is a special snowflake
class ASTToDate extends ASTUniPrefixOp {
  String _format;
  ASTToDate() { super(new String[]{"toDate", "x", "format"}); }
  @Override String opStr() { return "toDate"; }
  @Override ASTOp make() {return new ASTToDate();}
  @Override ASTToDate parse_impl(Exec E) {
    AST ast = E.parse();
    try {
      _format = ((ASTString)E.parse())._s;
    } catch (ClassCastException e) {
      throw new IllegalArgumentException("`format` must be a string.");
    }
    E.eatEnd(); // eat ending ')'
    ASTToDate res = (ASTToDate) clone();
    res._asts = new AST[]{ast};
    return res;
  }
  @Override void apply(Env env) {
    final String format = _format;
    if (format.isEmpty()) throw new IllegalArgumentException("toDate requires a non-empty format string");
    // check the format string more?

    Frame fr = env.popAry();

    if( fr.vecs().length != 1 || !(fr.vecs()[0].isEnum() || fr.vecs()[0].isString()))
      throw new IllegalArgumentException("toDate requires a single column of factors or strings");

    final String[] dom  = fr.anyVec().domain();
    final boolean isStr = dom==null && fr.anyVec().isString();
    if( !isStr )
      assert dom!=null : "toDate error: domain is null, but vec is not String";

    Frame fr2 = new MRTask() {
      private transient DateTimeFormatter _fmt;
      @Override public void setupLocal() {_fmt = DateTimeFormat.forPattern(format).withZone(ParseTime.getTimezone());}
      @Override public void map( Chunk c, NewChunk nc ) {
        //done on each node in lieu of rewriting DateTimeFormatter as Iced
        String date;
        ValueString vStr = new ValueString();
        for( int i=0; i<c._len; ++i ) {
          if( !c.isNA(i) ) {
            if( isStr ) date = c.atStr(vStr, i).toString();
            else        date = dom[(int)c.at8(i)];
            nc.addNum(DateTime.parse(date,_fmt).getMillis(),0);
          } else nc.addNA();
        }
      }
    }.doAll(1,fr).outputFrame(fr._names, null);
    env.pushAry(fr2);
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
    try {
      _digits = (int) ((ASTNum) (E.parse())).dbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Expected a number for `digits` argument.");
    }
    E.eatEnd(); // eat ending ')'
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
    // Get the digits
    try {
      _digits = (int) ((ASTNum) (E.parse())).dbl();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Expected a double for `digits` argument.");
    }
    E.eatEnd(); // eat ending ')'
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
    if (fr.numCols() == 1) {
      if (fr.anyVec().isEnum()) res = "TRUE";
      env.push(new ValStr(res));
    } else {
      Futures fs = new Futures();
      Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
      AppendableVec v = new AppendableVec(key);
      NewChunk chunk = new NewChunk(v, 0);
      for( int i=0;i<fr.numCols();++i ) chunk.addNum(fr.vec(i).isEnum()?1:0);
      chunk.close(0,fs);
      Vec vec = v.close(fs);
      fs.blockForPending();
      vec.setDomain(new String[]{"FALSE", "TRUE"});
      Frame fr2 = new Frame(Key.make(), new String[]{"C1"}, new Vec[]{vec});
      DKV.put(fr2);  // push this soggy frame into dkv, let R handle the rest...
      env.pushAry(fr2);
    }
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

class ASTNLevels extends ASTUniPrefixOp {
  ASTNLevels() { super(VARS1); }
  @Override String opStr() { return "nlevels"; }
  @Override ASTOp make() {return new ASTNLevels();}
  @Override void apply(Env env) {
    int nlevels;
    Frame fr = env.popAry();
    if (fr.numCols() != 1) {
      Futures fs = new Futures();
      Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
      AppendableVec v = new AppendableVec(key);
      NewChunk chunk = new NewChunk(v, 0);
      for( int i=0;i<fr.numCols();++i ) chunk.addNum(fr.vec(i).isEnum()?fr.vec(i).domain().length:0);
      chunk.close(0,fs);
      Vec vec = v.close(fs);
      fs.blockForPending();
      Frame fr2 = new Frame(Key.make(), new String[]{"C1"}, new Vec[]{vec});
      DKV.put(fr2);  // push this soggy frame into dkv, let R handle the rest...
      env.pushAry(fr2);
    } else {
      Vec v = fr.anyVec();
      nlevels = v.isEnum()?v.domain().length:0;
      env.push(new ValNum(nlevels));
    }
  }
}

class ASTLevels extends ASTUniPrefixOp {
  ASTLevels() { super(VARS1); }
  @Override String opStr() { return "levels"; }
  @Override ASTOp make() { return new ASTLevels(); }
  @Override void apply(Env e) {
    Frame f = e.popAry();
    Futures fs = new Futures();
    Key[] keys = Vec.VectorGroup.VG_LEN1.addVecs(f.numCols());
    Vec[] vecs = new Vec[keys.length];

    // compute the longest vec... that's the one with the most domain levels
    int max=0;
    for(int i=0;i<f.numCols();++i )
      if( f.vec(i).isEnum() )
        if( max < f.vec(i).domain().length ) max = f.vec(i).domain().length;

    for( int i=0;i<f.numCols();++i ) {
      AppendableVec v = new AppendableVec(keys[i]);
      NewChunk nc = new NewChunk(v,0);
      String[] dom = f.vec(i).domain();
      int numToPad = dom==null?max:max-dom.length;
      if( dom != null )
        for(int j=0;j<dom.length;++j) nc.addNum(j);
      for(int j=0;j<numToPad;++j)     nc.addNA();
      nc.close(0,fs);
      vecs[i] = v.close(fs);
      vecs[i].setDomain(dom);
    }
    fs.blockForPending();
    Frame fr2 = new Frame(Key.make(), null, vecs);
    DKV.put(fr2);  // push this soggy frame into dkv, let R handle the rest...
    e.pushAry(fr2);
  }
}

class ASTAll extends ASTUniPrefixOp {
  boolean _narm;
  ASTAll() { super(VARS1);}
  @Override String opStr() { return "all"; }
  @Override ASTOp make() {return new ASTAll();}
  ASTAll parse_impl(Exec E) {
    AST arg = E.parse();
    AST a = E.parse();
    if( a instanceof ASTId ) _narm = ((ASTNum)E._env.lookup((ASTId)a))._d==1; // ignroed for now, always assume narm=F
    E.eatEnd(); // eat ending ')'
    ASTAll res = (ASTAll) clone();
    res._asts = new AST[]{arg};
    return res;
  }
  @Override void apply(Env env) {
    boolean all;
    if( env.isNum() ) { all = env.popDbl()!=0; }  // got a number on the stack... if 0 then all is FALSE, otherwise TRUE
    else {
      Frame fr = env.popAry();
      if( fr.numCols() != 1 ) throw new IllegalArgumentException("must only have 1 column for `all`");
      Vec v = fr.anyVec();
      if( !v.isInt() ) throw new IllegalArgumentException("column must be a column of 1s and 0s.");
      if( v.min() != 0 || v.max() != 1 ) throw new IllegalArgumentException("column must be a column of 1s and 0s");
      all = new AllTask().doAll(fr.anyVec()).all;
    }
    env.push(new ValStr(all?"TRUE":"FALSE"));
  }

  private static class AllTask extends MRTask<AllTask> {
    private boolean all=true;
    @Override public void map(Chunk c) {
      for(int i=0;i<c._len;++i) {
        if( all ) {
          if( c.isNA(i) ) { all = false; break; }
          all &= c.atd(i)==1;
        }
      }
    }
    @Override public void reduce(AllTask t) { all &= t.all; }
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
  boolean _center=true;  //default
  double[] _centers;
  boolean _scale=true;  //default
  double[] _scales;
  ASTScale() { super(new String[]{"ary", "center", "scale"});}
  @Override String opStr() { return "scale"; }
  @Override ASTOp make() {return new ASTScale();}
  ASTScale parse_impl(Exec E) {
    AST ary = E.parse();
    parseArg(E, true);  // centers parse
    parseArg(E, false); // scales parse
    E.eatEnd(); // eat ending ')'
    ASTScale res = (ASTScale) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  private void parseArg(Exec E, boolean center) {
    if( center ) {
      String[] centers = E.peek() == '{' ? E.xpeek('{').parseString('}').split(";") : null;
      if (centers == null) {
        // means `center` is boolean
        AST a;
        try {
          AST e = E.parse();
          a = E._env.lookup((ASTId) e);  // looking up TRUE / FALSE
        } catch (ClassCastException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Expected %TRUE or %FALSE. Got: " + e.getClass());
        }
        try {
          _center = ((ASTNum) a).dbl() == 1;
          _centers = null;
        } catch (ClassCastException e) {
          e.printStackTrace();
          throw new IllegalArgumentException("Expected to get a number for the `center` argument after the lookup. Got: " + a.getClass());
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

abstract class ASTTimeOp extends ASTUniPrefixOp {
  ASTTimeOp() { super(VARS1); }
  // Override for e.g. month and day-of-week
  protected String[][] factors() { return null; }
  @Override ASTTimeOp parse_impl(Exec E) {
    AST arg = E.parse();
    ASTTimeOp res = (ASTTimeOp) clone();
    E.eatEnd(); // eat ending ')'
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
    }.doAll(fr.numCols(),fr).outputFrame(fr._names, factors());
    env.poppush(1, new ValFrame(fr2));
  }
}
// As a policy we return what JODA returns, it is left to the client wrappers to convert the answer to a language consistent answer
class ASTYear   extends ASTTimeOp { @Override String opStr(){ return "year" ; } @Override ASTOp make() {return new ASTYear  ();} @Override long op(MutableDateTime dt) { return dt.getYear();}}
class ASTDay    extends ASTTimeOp { @Override String opStr(){ return "day"  ; } @Override ASTOp make() {return new ASTDay   ();} @Override long op(MutableDateTime dt) { return dt.getDayOfMonth();}}
class ASTHour   extends ASTTimeOp { @Override String opStr(){ return "hour" ; } @Override ASTOp make() {return new ASTHour  ();} @Override long op(MutableDateTime dt) { return dt.getHourOfDay();}}
class ASTMinute extends ASTTimeOp { @Override String opStr(){ return "minute";} @Override ASTOp make() {return new ASTMinute();} @Override long op(MutableDateTime dt) { return dt.getMinuteOfHour();}}
class ASTSecond extends ASTTimeOp { @Override String opStr(){ return "second";} @Override ASTOp make() {return new ASTSecond();} @Override long op(MutableDateTime dt) { return dt.getSecondOfMinute();}}
class ASTMillis extends ASTTimeOp { @Override String opStr(){ return "millis";} @Override ASTOp make() {return new ASTMillis();} @Override long op(MutableDateTime dt) { return dt.getMillisOfSecond();}}
class ASTMonth  extends ASTTimeOp { @Override String opStr(){ return "month"; } @Override ASTOp make() {return new ASTMonth ();} @Override long op(MutableDateTime dt) { return dt.getMonthOfYear();}}
class ASTWeek   extends ASTTimeOp { @Override String opStr(){ return "week";  } @Override ASTOp make() {return new ASTWeek  ();} @Override long op(MutableDateTime dt) { return dt.getWeekOfWeekyear();}}

class ASTDayOfWeek extends ASTTimeOp {
  static private final String[][] FACTORS = new String[][]{{"Mon","Tue","Wed","Thu","Fri","Sat","Sun"}}; // Order comes from Joda
  @Override protected String[][] factors() { return FACTORS; }
  @Override String opStr(){ return "dayOfWeek"; }
  @Override ASTOp make() {return new ASTDayOfWeek();}
  @Override long op(MutableDateTime dt) { return dt.getDayOfWeek()-1;}
}

// Convert year, month, day, hour, minute, sec, msec to Unix epoch time
class ASTMktime extends ASTUniPrefixOp {
  ASTMktime() { super(new String[]{"","year","month","day","hour","minute","second","msec"}); }
  @Override String opStr() { return "mktime"; }
  @Override ASTMktime make() {return new ASTMktime();}
  @Override ASTMktime parse_impl(Exec E) {
    AST yr = E.parse();
    AST mo = E.parse();
    AST dy = E.parse();
    AST hr = E.parse();
    AST mi = E.parse();
    AST se = E.parse();
    AST ms = E.parse();
    E.eatEnd(); // eat ending ')'
    ASTMktime res = (ASTMktime) clone();
    res._asts = new AST[]{yr,mo,dy,hr,mi,se,ms};
    return res;
  }

  @Override void apply(Env env) {
    // Seven args, all required.  See if any are arrays.
    Frame fs[] = new Frame[7];
    int   is[] = new int  [7];
    Frame x = null;             // Sample frame (for auto-expanding constants)
    for( int i=0; i<7; i++ )
      if( env.peekType()==Env.ARY ) fs[i] = x = env.popAry();
      else                          is[i] =(int)env.popDbl();

    if( x==null ) {                            // Single point
      long msec = new MutableDateTime(is[6],   // year
              is[5]+1, // month
              is[4]+1, // day
              is[3],   // hour
              is[2],   // minute
              is[1],   // second
              is[0])   // msec
              .getMillis();
      env.poppush(1, new ValNum(msec));
      return;
    }

    // Make constant Vecs for the constant args.  Commonly, they'll all be zero
    Vec vecs[] = new Vec[7];
    for( int i=0; i<7; i++ ) {
      if( fs[i] == null ) {
        vecs[i] = x.anyVec().makeCon(is[i]);
      } else {
        if( fs[i].numCols() != 1 ) throw new IllegalArgumentException("Expect single column");
        vecs[i] = fs[i].anyVec();
      }
    }

    // Convert whole column to epoch msec
    Frame fr2 = new MRTask() {
      @Override public void map( Chunk chks[], NewChunk nchks[] ) {
        MutableDateTime dt = new MutableDateTime(0);
        NewChunk n = nchks[0];
        int rlen = chks[0]._len;
        for( int r=0; r<rlen; r++ ) {
          dt.setDateTime((int)chks[6].at8(r),  // year
                  (int)chks[5].at8(r)+1,// month
                  (int)chks[4].at8(r)+1,// day
                  (int)chks[3].at8(r),  // hour
                  (int)chks[2].at8(r),  // minute
                  (int)chks[1].at8(r),  // second
                  (int)chks[0].at8(r)); // msec
          n.addNum(dt.getMillis());
        }
      }
    }.doAll(1,vecs).outputFrame(new String[]{"msec"},null);
    // Clean up the constants
    for( int i=0; i<7; i++ )
      if( fs[i] == null )
        vecs[i].remove();
    env.poppush(1, new ValFrame(fr2));
  }
}

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
abstract class ASTBinOp extends ASTUniOrBinOp {

  ASTBinOp() { super(VARS2); } // binary ops are infix ops

  ASTBinOp parse_impl(Exec E) {
    AST l = E.parse();
    AST r = E.parse();
    E.eatEnd(); // eat ending ')'
    ASTBinOp res = (ASTBinOp) clone();
    res._asts = new AST[]{l,r};
    return res;
  }

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
      default: throw H2O.unimpl("Got unusable type: " + left_type + " in binary operator " + opStr());
    }

    // Cast the RHS of the op
    switch(right_type) {
      case Env.NUM: d1  = ((ValNum)right)._d; break;
      case Env.ARY: fr1 = ((ValFrame)right)._fr; break;
      case Env.STR: s1  = ((ValStr)right)._s; break;
      default: throw H2O.unimpl("Got unusable type: " + right_type + " in binary operator " + opStr());
    }

    // If both are doubles on the stack
    if( (fr0==null && fr1==null) && (s0==null && s1==null) ) { env.poppush(2, new ValNum(op(d0, d1))); return; }

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

    if( fr0!=null ) {
      if( fr0.numCols()==1 && fr0.numRows()==1 ) {
        Vec v = fr0.anyVec();
        if( v.isEnum() ) s0 = v.domain()[(int)v.at(0)];
        else             d0 = v.at(0);
        fr0=null;
      }
    }

    if( fr1!=null ) {
      if( fr1.numCols()==1 && fr1.numRows()==1 ) {
        Vec v = fr1.anyVec();
        if( v.isEnum() ) s1 = v.domain()[(int)v.at(0)];
        else             d1 = v.at(0);
        fr1=null;
      }
    }

    // both were 1x1 frames on the stack...
    if( fr0==null && fr1==null ) {
      if( s0==null && s1==null ) env.poppush(2, new ValNum(op(d0, d1)));
      if( s0!=null && s1==null ) env.poppush(2, new ValNum(Double.valueOf(op(s0, d1))));
      if( s0==null && s1!=null ) env.poppush(2, new ValNum(Double.valueOf(op(d0, s1))));
      if( s0!=null && s1!=null ) env.poppush(2, new ValNum(Double.valueOf(op(s0, s1))));
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
    }.doAll(ncols,fr).outputFrame(null, (lf ? fr0 : fr1)._names,null);
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
class ASTIntDiv extends ASTBinOp { public ASTIntDiv() { super(); } @Override String opStr(){ return "intDiv"; } @Override ASTOp make() { return new ASTIntDiv();}
  @Override double op(double d0, double d1) { return (int)d0/(int)d1;}
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
class ASTMod extends ASTBinOp { public ASTMod() { super(); } @Override String opStr(){ return "mod"; } @Override ASTOp make() {return new ASTMod ();}
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

class O extends ASTOp {
  // example (O "a" "sum" "b" "null" (+ %a %b))
  // this is (O _accName _acc _elemName _elem AST)
  String _accName; // the name of the accumulator variable
  String _acc;     // the accumulator
  String _elemName; // the name of the element variable
  String _elem; // the new element, if null, then use the chunk.at(i) value.
  O() { super(null); }
  @Override String opStr() { return "O"; }
  @Override ASTOp make() { return new O(); }
  O parse_impl(Exec E) {
    _accName = E.parseString(E.peekPlus());
    _acc     = E.parseString(E.peekPlus());
    _elemName= E.parseString(E.peekPlus());
    AST elem = E.parse();
    if( elem instanceof ASTNum )         _elem=""+((ASTNum)elem)._d;
    else if( elem instanceof ASTString ) _elem=((ASTString)elem)._s;
    if( _elem.equals("null") )           _elem=null;
    AST ast = E.parse();  // directions on how to accumulate
    E.eatEnd(); // eat ending ')'
    O res = (O)clone();
    res._asts = new AST[]{ast};
    return res;
  }
  @Override void apply(Env e) { }
  void exec(NonBlockingHashMap<String,Val> m, Chunk c, int row) {
    Env e = (new Env(null)).capture();
    Val v = m.get(_acc);
    // if v is not null use the type, otherwise use the type of the elem!
    if( v!=null ) e._local.put(_accName, v.type(), v.value());
    int t=Env.NULL;
    if( _elem==null ) {
      if (c.vec().isNumeric())
        e._local.put(_elemName, t=Env.NUM, ""+c.atd(row));
      else if (c.vec().isString())
        e._local.put(_elemName, t=Env.STR, c.atStr(new ValueString(), row).toString());
    } else {
      int type;
      try {
        Double.valueOf(_elem);
        type=Env.NUM;
      } catch(Exception ex) { type=Env.STR; }
      e._local.put(_elemName, t=type, _elem);
    }
    if( v==null )
      e._local.put(_accName, t, t==Env.STR?"":"0");  // currently expects only Strings or Nums...
    _asts[0].treeWalk(e);
    m.put(_acc,e.pop());
  }
  void reduce(NonBlockingHashMap<String,Val> thiz, NonBlockingHashMap<String,Val> that) {
    Env e =(new Env(null)).capture();
    Val l = thiz.get(_acc);
    Val r = that.get(_acc);
    e._local.put(_accName, l.type(),l.value());
    e._local.put(_elemName,r.type(),r.value());
    _asts[0].treeWalk(e);
    thiz.put(_acc,e.pop());
  }
}

class ROp extends ASTOp {
  HashMap<String, O> _ops;
  // parse_impl: (R accum1 O accum2 O ...)
  ROp() {super(null); _ops=new HashMap<>(); }
  @Override String opStr() { return "R"; }
  @Override ASTOp make() { return new ROp(); }
  ROp parse_impl(Exec E) {
    while( !E.isEnd() ) {
      String acc = E.parseString(E.peekPlus());
      O o = (O)E.parse();
      _ops.put(acc,o);
    }
    E.eatEnd(); // eat ending ')'
    return (ROp)clone();
  }
  void map(NonBlockingHashMap<String,Val> m, Chunk c, int row) {
    for( String s:_ops.keySet() )
      _ops.get(s).exec(m,c,row);
  }
  void reduce(NonBlockingHashMap<String,Val> thiz, NonBlockingHashMap<String,Val> that) {
    for( String s:_ops.keySet())
      _ops.get(s).reduce(thiz,that);
  }
  @Override public AutoBuffer write_impl(AutoBuffer ab) {
    if( _ops==null ) return ab.put4(0);
    ab.put4(_ops.size());
    for( String s:_ops.keySet()) { ab.putStr(s); ab.put(_ops.get(s)); }
    return ab;
  }
  @Override public ROp read_impl(AutoBuffer ab) {
    int len = ab.get4();
    if( len==0 ) return this;
    _ops = new HashMap<>();
    for( int i=0;i<len;++i)
      _ops.put(ab.getStr(), ab.get(O.class));
    return this;
  }
  @Override void exec(Env e) {}
  @Override String value() { return null; }
  @Override int type() { return 0; }
  @Override public void apply(Env e) {}
}

class COp extends ASTOp {
  COp() {super(null);}
  @Override String opStr() { return "C"; }
  @Override ASTOp make() { return new COp(); }
  // parse_impl: (C (AST))
  COp parse_impl(Exec E) {
    AST ast = E.parse();
    E.eatEnd(); // eat ending ')'
    COp res = (COp)clone();
    res._asts = new AST[]{ast};
    return res;
  }
  Val combine(NonBlockingHashMap<String,Val> m) {
    Env e = (new Env(null)).capture();
    for( String s:m.keySet() ) {
      e._local.put(s,m.get(s).type(),m.get(s).value());
    }
    _asts[0].treeWalk(e);
    return e.pop();
  }
  @Override void exec(Env e) {}
  @Override String value() { return null; }
  @Override int type() { return 0; }
  @Override public void apply(Env e) {}
}

// operate on a single vec
// reduce the Vec
class ASTFoldCombine extends ASTUniPrefixOp {
  // (RC ary (R ...) (C ...))
  private ROp _red;     // operates on a single value
  private COp _combine; // what to do with the _accum map
  ASTFoldCombine() { super(null); }
  @Override String opStr() { return "RC"; }
  @Override ASTOp make() { return new ASTFoldCombine(); }
  ASTFoldCombine parse_impl(Exec E) {
    AST ary =  E.parse();
    _red = (ROp)E.parse();
    _combine = (COp)E.parse();
    E.eatEnd(); // eat ending ')'
    ASTFoldCombine res = (ASTFoldCombine) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env e) {
    Frame f = e.popAry();
    if( f.numCols() != 1 )
      throw new IllegalArgumentException("Expected one column, got "+f.numCols());

    // apply _red across the frame f and fetch out _accum
    NonBlockingHashMap<String,Val> accum = new RTask(_red).doAll(f.anyVec())._accum;

    // then apply the _combine operator on the accum...
    e.push(_combine.combine(accum));
  }

  private static class RTask extends MRTask<RTask> {
    NonBlockingHashMap<String,Val> _accum;
    private ROp _red;
    RTask(ROp red) { _red=red; }
    @Override public void setupLocal() {_accum=new NonBlockingHashMap<>();}
    @Override public void map(Chunk cs) {
      for( int i=0;i<cs._len;++i)
        _red.map(_accum,cs,i);
    }
    @Override public void reduce(RTask t) { _red.reduce(_accum,t._accum); }
    @Override public AutoBuffer write_impl( AutoBuffer ab ) {
      ab.put(_red);
      if( _accum == null ) return ab.put4(0);
      ab.put4(_accum.size());
      for( String s:_accum.keySet() ) {
        ab.putStr(s);
        ab.put(_accum.get(s));
      }
      return ab;
    }
    @Override public RTask read_impl(AutoBuffer ab) {
      _red = ab.get(ROp.class);
      int len = ab.get4();
      if( len == 0 ) return this;
      _accum = new NonBlockingHashMap<>();
      for( int i=0;i<len;++i )
        _accum.put(ab.getStr(), ab.get(Val.class));
      return this;
    }
  }
}

// Variable length; instances will be created of required length
abstract class ASTReducerOp extends ASTOp {
  double _init;
  boolean _narm;        // na.rm in R
  int _argcnt;
  ASTReducerOp( double init) {
    super(new String[]{"","dblary","...", "na.rm"});
    _init = init;
  }

  ASTReducerOp parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST a;

    do {  // rely on breaks
      a = E.parse();
      if( a instanceof ASTId ) {
        if( Env.staticLookup((ASTId) a ) instanceof ASTFrame) dblarys.add(a); // kv lookup
        if( E._env.tryLookup((ASTId)a) ) break;
        else dblarys.add(a);
      } else if( a instanceof ASTAssign || a instanceof ASTNum || a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTOp ) dblarys.add(a);
      else break;
    } while( !E.isEnd() );

    // Get the na.rm last
    if( !E.isEnd() ) {
      a = E.parse();
      if( a instanceof ASTId ) a = E._env.lookup((ASTId)a);
      else throw new IllegalArgumentException("Expected the na.rm value to be one of %TRUE, %FALSE, %T, %F");
      _narm = ((ASTNum)a).dbl() == 1;
    } else { _narm=true; }

    E.eatEnd(); // eat ending ')'
    AST[] arys = new AST[_argcnt = dblarys.size()];
    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
    ASTReducerOp res = (ASTReducerOp) clone();
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

  @Override void exec(Env e, AST[] args) {
    _argcnt = args.length;
    // extra args are init and narm
    _init=0;
    _narm=true;
    args[0].exec(e);
    e.put(Key.make().toString(), e.peekAry());
    apply(e);
  }

  private static class RedOp extends MRTask<RedOp> {
    final ASTReducerOp _bin;
    RedOp( ASTReducerOp bin ) { _bin = bin; _d = bin._init; }
    double _d;
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
        assert C.vec().isNumeric();
        double sum = _d;
        for (int r = 0; r < rows; r++)
          sum = _bin.op(sum, C.atd(r));
        _d = sum;
        if (Double.isNaN(sum)) break;
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
        assert C.vec().isNumeric();
        double sum = _d;
        for (int r = 0; r < rows; r++) {
          double d = C.atd(r);
          if (!Double.isNaN(d))
            sum = _bin.op(sum, d);
        }
        _d = sum;
        if (Double.isNaN(sum)) break;
      }
    }
    @Override public void reduce( NaRmRedOp s ) { _d = _bin.op(_d, s._d); }
  }
}

class ASTSum extends ASTReducerOp {
  ASTSum() {super(0);}
  @Override String opStr(){ return "sum";}
  @Override ASTOp make() {return new ASTSum();}
  @Override double op(double d0, double d1) { return d0+d1;}
  @Override void apply(Env env) {
    double sum=_init;
    int argcnt = _argcnt;
    for( int i=0; i<argcnt; i++ )
      if( env.isNum() ) sum = op(sum,env.popDbl());
      else {
        Frame fr = env.popAry(); // pop w/o lowering refcnts ... clean it up later
        for(Vec v : fr.vecs()) if (v.isEnum() || v.isUUID() || v.isString()) throw new IllegalArgumentException("`"+opStr()+"`" + " only defined on a data frame with all numeric variables");
        sum += new RedSum(_narm).doAll(fr)._d;
      }
    env.push(new ValNum(sum));
  }

  private static class RedSum extends MRTask<RedSum> {
    final boolean _narm;
    double _d;
    RedSum( boolean narm ) { _narm = narm; }
    @Override public void map( Chunk chks[] ) {
      int rows = chks[0]._len;
      for (Chunk C : chks) {
//        assert C.vec().isNumeric();
        double sum=_d;
        if( _narm ) for (int r = 0; r < rows; r++) { double d = C.atd(r); if( !Double.isNaN(d) ) sum += d; }
        else        for (int r = 0; r < rows; r++) { double d = C.atd(r);                        sum += d; }
        _d = sum;
        if( Double.isNaN(sum) ) break;
      }
    }
    @Override public void reduce( RedSum s ) { _d += s._d; }
  }
}

class ASTCumSum extends ASTUniPrefixOp {
  @Override String opStr() { return "cumsum"; }
  @Override ASTOp make() { return new ASTCumSum(); }
  public ASTCumSum() { super(new String[]{"x"}); }

  @Override public void apply(Env e){
    Frame f = e.popAry();

    // per chunk cum-sum
    CumSumTask t = new CumSumTask(f.anyVec().nChunks());
    t.doAll(1, f.anyVec());
    final double[] chkSums = t._chkSums;
    Vec cumuVec = t.outputFrame().anyVec();
    new MRTask() {
      @Override public void map(Chunk c) {
        double d=c.cidx()==0?0:chkSums[c.cidx()-1];
        for(int i=0;i<c._len;++i)
          c.set(i, c.atd(i)+d);
      }
    }.doAll(cumuVec);
    e.pushAry(new Frame(cumuVec));
  }

  private class CumSumTask extends MRTask<CumSumTask> {
    //IN
    final int _nchks;

    //OUT
    double[] _chkSums;

    CumSumTask(int nchks) { _nchks = nchks; }
    @Override public void setupLocal() { _chkSums = new double[_nchks]; }
    @Override public void map(Chunk c, NewChunk nc) {
      double sum=0;
      for(int i=0;i<c._len;++i) {
        sum += c.isNA(i) ? Double.NaN : c.atd(i);
        if( Double.isNaN(sum) ) nc.addNA();
        else                    nc.addNum(sum);
      }
      _chkSums[c.cidx()] = sum;
    }
    @Override public void reduce(CumSumTask t) { if( _chkSums != t._chkSums ) ArrayUtils.add(_chkSums, t._chkSums); }
    @Override public void postGlobal() {
      // cumsum the _chunk_sums array
      for(int i=1;i<_chkSums.length;++i) _chkSums[i] += _chkSums[i-1];
    }
  }
}

class ASTCumProd extends ASTUniPrefixOp {
  @Override String opStr() { return "cumprod"; }
  @Override ASTOp make() { return new ASTCumProd(); }
  public ASTCumProd() { super(new String[]{"x"}); }

  @Override public void apply(Env e){
    Frame f = e.popAry();

    // per chunk cum-prod
    CumProdTask t = new CumProdTask(f.anyVec().nChunks());
    t.doAll(1, f.anyVec());
    final double[] chkProds = t._chkProds;
    Vec cumuVec = t.outputFrame().anyVec();
    new MRTask() {
      @Override public void map(Chunk c) {
        if( c.cidx()!=0 ) {
          double d=chkProds[c.cidx()-1];
          for(int i=0;i<c._len;++i)
            c.set(i, c.atd(i)*d);
        }
      }
    }.doAll(cumuVec);
    e.pushAry(new Frame(cumuVec));
  }

  private class CumProdTask extends MRTask<CumProdTask> {
    //IN
    final int _nchks;

    //OUT
    double[] _chkProds;

    CumProdTask(int nchks) { _nchks = nchks; }
    @Override public void setupLocal() { _chkProds = new double[_nchks]; }
    @Override public void map(Chunk c, NewChunk nc) {
      double sum=0;
      for(int i=0;i<c._len;++i) {
        sum *= c.isNA(i) ? Double.NaN : c.atd(i);
        if( Double.isNaN(sum) ) nc.addNA();
        else                    nc.addNum(sum);
      }
      _chkProds[c.cidx()] = sum;
    }
    @Override public void reduce(CumProdTask t) { if( _chkProds != t._chkProds ) ArrayUtils.add(_chkProds, t._chkProds); }
    @Override public void postGlobal() {
      // cumsum the _chunk_sums array
      for(int i=1;i<_chkProds.length;++i) _chkProds[i] *= _chkProds[i-1];
    }
  }
}

class ASTCumMin extends ASTUniPrefixOp {
  @Override String opStr() { return "cummin"; }
  @Override ASTOp make() { return new ASTCumMin(); }
  public ASTCumMin() { super(new String[]{"x"}); }

  @Override public void apply(Env e){
    Frame f = e.popAry();

    // per chunk cum-min
    CumMinTask t = new CumMinTask(f.anyVec().nChunks());
    t.doAll(1, f.anyVec());
    final double[] chkMins = t._chkMins;
    Vec cumuVec = t.outputFrame().anyVec();
    new MRTask() {
      @Override public void map(Chunk c) {
        if( c.cidx()!=0 ) {
          double d=chkMins[c.cidx()-1];
          for(int i=0;i<c._len;++i)
            c.set(i, Math.min(c.atd(i), d));
        }
      }
    }.doAll(cumuVec);
    e.pushAry(new Frame(cumuVec));
  }

  private class CumMinTask extends MRTask<CumMinTask> {
    //IN
    final int _nchks;

    //OUT
    double[] _chkMins;

    CumMinTask(int nchks) { _nchks = nchks; }
    @Override public void setupLocal() { _chkMins = new double[_nchks]; }
    @Override public void map(Chunk c, NewChunk nc) {
      double min=Double.MAX_VALUE;
      for(int i=0;i<c._len;++i) {
        min = c.isNA(i) ? Double.NaN : Math.min(min, c.atd(i));
        if( Double.isNaN(min) ) nc.addNA();
        else                    nc.addNum(min);
      }
      _chkMins[c.cidx()] = min;
    }
    @Override public void reduce(CumMinTask t) { if( _chkMins != t._chkMins ) ArrayUtils.add(_chkMins, t._chkMins); }
    @Override public void postGlobal() {
      // cumsum the _chunk_sums array
      for(int i=1;i<_chkMins.length;++i)
        _chkMins[i] = _chkMins[i-1] < _chkMins[i] ? _chkMins[i-1] : _chkMins[i];
    }
  }
}

class ASTCumMax extends ASTUniPrefixOp {
  @Override String opStr() { return "cummax"; }
  @Override ASTOp make() { return new ASTCumMax(); }
  public ASTCumMax() { super(new String[]{"x"}); }

  @Override public void apply(Env e){
    Frame f = e.popAry();

    // per chunk cum-min
    CumMaxTask t = new CumMaxTask(f.anyVec().nChunks());
    t.doAll(1, f.anyVec());
    final double[] chkMaxs = t._chkMaxs;
    Vec cumuVec = t.outputFrame().anyVec();
    new MRTask() {
      @Override public void map(Chunk c) {
        if( c.cidx()!=0 ) {
          double d=chkMaxs[c.cidx()-1];
          for(int i=0;i<c._len;++i)
            c.set(i, Math.min(c.atd(i), d));
        }
      }
    }.doAll(cumuVec);
    e.pushAry(new Frame(cumuVec));
  }

  private class CumMaxTask extends MRTask<CumMaxTask> {
    //IN
    final int _nchks;

    //OUT
    double[] _chkMaxs;

    CumMaxTask(int nchks) { _nchks = nchks; }
    @Override public void setupLocal() { _chkMaxs = new double[_nchks]; }
    @Override public void map(Chunk c, NewChunk nc) {
      double max=-Double.MAX_VALUE;
      for(int i=0;i<c._len;++i) {
        max = c.isNA(i) ? Double.NaN : Math.max(max, c.atd(i));
        if( Double.isNaN(max) ) nc.addNA();
        else                    nc.addNum(max);
      }
      _chkMaxs[c.cidx()] = max;
    }
    @Override public void reduce(CumMaxTask t) { if( _chkMaxs != t._chkMaxs ) ArrayUtils.add(_chkMaxs, t._chkMaxs); }
    @Override public void postGlobal() {
      // cumsum the _chunk_sums array
      for(int i=1;i<_chkMaxs.length;++i)
        _chkMaxs[i] = _chkMaxs[i-1] > _chkMaxs[i] ? _chkMaxs[i-1] : _chkMaxs[i];
    }
  }
}

class ASTKappa extends ASTUniPrefixOp {

  int _nclass;
  @Override String opStr() { return "kappa"; }
  @Override ASTOp make() { return new ASTKappa(); }
  public ASTKappa() { super(new String[]{"actual", "pred", "nclass"});}
  ASTKappa parse_impl(Exec E) {
    AST act = E.parse();
    AST pre = E.parse();
    _nclass = (int)E.nextDbl();
    E.eatEnd();
    ASTKappa res = (ASTKappa)clone();
    res._asts = new AST[]{act,pre};
    return res;
  }

  @Override public void apply(Env e) {
    Frame act = e.popAry();
    Frame pre = e.popAry();

    OMatrixTask t = new OMatrixTask(_nclass).doAll((act.add(pre)));
    final int[][] O = t._O;

    double numerator=0;
    double denominator=0;
    for(int i=0;i<_nclass;++i)
      for(int j=0;j<_nclass;++j) {
        double w_ij = (double)((i-j)*(i-j)) / (double)((_nclass-1)*(_nclass-1));
        double e_ij = (double)(t._aHist[i] * t._pHist[j]) / (double)act.numRows();
        numerator+= w_ij*O[i][j];
        denominator+=w_ij*e_ij;
      }
    e.push(new ValNum(1-numerator/denominator));
  }

  private static class OMatrixTask extends MRTask<OMatrixTask> {
    final int _n;
    int[][] _O;
    int[] _aHist;
    int[] _pHist;
    OMatrixTask(int n) { _n=n; }

    @Override public void map(Chunk[] cs) {
      _O = new int[_n][_n];
      _aHist = new int[_n];
      _pHist = new int[_n];
      for(int i=0;i<cs[0]._len;++i) {
        int x = (int)cs[0].at8(i);
        int y = (int)cs[1].at8(i);
        _aHist[x]++;
        _pHist[y]++;
        _O[x][y]++;
      }
    }
    @Override public void reduce(OMatrixTask t) { _O=ArrayUtils.add(_O, t._O); _aHist=ArrayUtils.add(_aHist, t._aHist); _pHist=ArrayUtils.add(_pHist,t._pHist);  t._O=null; }
  }
}

class ASTImpute extends ASTUniPrefixOp {
  ImputeMethod _method;
  long[] _by;
  int _colIdx;
  boolean _inplace;
  int _maxGap;
  QuantileModel.CombineMethod _combine_method;  // for quantile only
  private static enum ImputeMethod { MEAN , MEDIAN, MODE }
  @Override String opStr() { return "h2o.impute"; }
  @Override ASTOp make() { return new ASTImpute(); }
  public ASTImpute() { super(new String[]{"vec", "method", "combine_method", "by", "inplace"}); }
  ASTImpute parse_impl(Exec E) {
    AST ary = E.parse();
    _colIdx = (int)E.nextDbl();
    _method = ImputeMethod.valueOf(E.nextStr().toUpperCase());
    _combine_method = QuantileModel.CombineMethod.valueOf(E.nextStr().toUpperCase());

    AST a = E.parse();
    if( a instanceof ASTLongList ) _by = ((ASTLongList)a)._l;
    else if( a instanceof ASTNum ) _by = new long[]{(long)((ASTNum)a)._d};
    else _by=null;


    // should be TRUE or FALSE next, for the inplace arg
    a = E.parse();
    if( a instanceof ASTId ) _inplace = ((ASTNum)E._env.lookup((ASTId)a))._d==1;
//    _maxGap = (int)E.nextDbl(); // TODO
    E.eatEnd();
    ASTImpute res = (ASTImpute) clone();
    res._asts = new AST[]{ary};
    res._by = _by;    // just in case
    res._method = _method; // just in case
    return res;
  }
  @Override public void apply(Env e) {
    Frame f = e.popAry();
    Vec v = f.vecs()[_colIdx];
    Frame f2;
    final double imputeValue;

    // vanilla impute. no group by
    if( _by == null ) {
      if( !v.isNumeric() && _method!=ImputeMethod.MODE ) {
        Log.info("Can only impute non-numeric columns with the mode.");
        _method = ImputeMethod.MODE;
      }
      switch( _method ) {
        case MEAN:   imputeValue = ASTVar.getMean(v,true,""); break;
        case MEDIAN: imputeValue = ASTMedian.median(v, _combine_method); break;
        case MODE:   imputeValue = mode(v); break;
        default:
          throw H2O.unimpl("Unknown type: " + _method);
      }
      if( _inplace ) {
        new MRTask() {
          @Override public void map(Chunk c) {
            for(int i=0;i<c._len;++i)
              if( c.isNA(i) ) c.set(i, imputeValue);
          }
        }.doAll(v);
        f2=f;
      } else {
        // create a new vec by imputing the old.
        f2 = new MRTask() {
          @Override public void map(Chunk c, NewChunk n) {
            for (int i = 0; i < c._len; ++i)
              n.addNum(c.isNA(i) ? imputeValue : c.atd(i));
          }
        }.doAll(1, v).outputFrame(null, new String[]{f.names()[_colIdx]}, new String[][]{f.domains()[_colIdx]});
      }
    } else {
      if (_method == ImputeMethod.MEDIAN)
        throw H2O.unimpl("Currently cannot impute with the median over groups. Try mean.");
      ASTGroupBy.AGG[] agg = new ASTGroupBy.AGG[]{new ASTGroupBy.AGG("mean", _colIdx, "rm", "_avg", null, null)};
      ASTGroupBy.GBTask t = new ASTGroupBy.GBTask(_by, agg).doAll(f);
      final ASTGroupBy.IcedNBHS<ASTGroupBy.G> s=new ASTGroupBy.IcedNBHS<>(); s.addAll(t._g.keySet());
      final int nGrps = t._g.size();
      final ASTGroupBy.G[] grps = t._g.keySet().toArray(new ASTGroupBy.G[nGrps]);
      H2O.submitTask(new ASTGroupBy.ParallelPostGlobal(grps, nGrps,null)).join();
      final long[] cols = _by;
      final int colIdx = _colIdx;
      if( _inplace ) {
        new MRTask() {
          transient ASTGroupBy.IcedNBHS<ASTGroupBy.G> _s;
          @Override public void setupLocal() { _s = s; }
          @Override public void map(Chunk[] c) {
            ASTGroupBy.G g = new ASTGroupBy.G(cols.length);
            double impute_value;
            Chunk ch = c[colIdx];
            for (int i = 0; i < c[0]._len; ++i) {
              g.fill(i, c, cols);
              impute_value = _s.get(g)._avs[0]; //currently only have the mean
              if( ch.isNA(i) ) ch.set(i,impute_value);
            }
          }
        }.doAll(f);
        f2 = f;
      } else {
        f2 = new MRTask() {
          transient ASTGroupBy.IcedNBHS<ASTGroupBy.G> _s;
          @Override public void setupLocal() { _s = s; }
          @Override public void map(Chunk[] c, NewChunk n) {
            ASTGroupBy.G g = new ASTGroupBy.G(cols.length);
            double impute_value;
            Chunk ch = c[colIdx];
            for (int i = 0; i < c[0]._len; ++i) {
              g.fill(i, c, cols);
              impute_value = _s.get(g)._avs[0]; //currently only have the mean
              n.addNum(ch.isNA(i) ? impute_value : ch.atd(i));
            }
          }
        }.doAll(1, f).outputFrame(null, new String[]{f.names()[_colIdx]}, new String[][]{f.domains()[_colIdx]});
      }
    }
    e.push(new ValFrame(f2));
  }

  private double mode(Vec v) { return (new ModeTask((int)v.max())).doAll(v)._max; }
  private static class ModeTask extends MRTask<ModeTask> {
    // compute the mode of an enum column as fast as possible
    int _m;   // max of the column... only for setting the size of _cnts
    long _max; // updated atomically
    long[] _cnts; // keep an array of counts, updated atomically , long for compareAndSwapLong
    static private final long _maxOffset;
    private static final Unsafe U = UtilUnsafe.getUnsafe();
    private static final int _b = U.arrayBaseOffset(long[].class);
    private static final int _s = U.arrayIndexScale(long[].class);
    private static long ssid(int i) { return _b + _s*i; } // Scale and Shift
    static {
      try {
        _maxOffset = U.objectFieldOffset(ModeTask.class.getDeclaredField("_max"));
      } catch( Exception e ) {
        throw new RuntimeException("golly mistah, I crashed :(!");
      }
    }

    ModeTask(int m) {_m=m;}
    @Override public void setupLocal() {
      _cnts = MemoryManager.malloc8(_m+1);
      _max = Integer.MIN_VALUE;
    }
    @Override public void map(Chunk c) {
      for( int i=0;i<c._len;++i ) {
        if( !c.isNA(i) ) {
          int h = (int) c.at8(i);
          long offset = ssid(h);
          long cnt = _cnts[h];
          while (!U.compareAndSwapLong(_cnts, offset, cnt, cnt + 1))
            cnt = _cnts[h];
        }
      }
    }
    @Override public void postGlobal() {
      int maxIdx=0;
      assert _cnts!=null;
      for(int i=0; i<_cnts.length;++i) {  // FIXME: possibly horrible to do single threaded hunt... use FJ task to find indx with max.
        if( _cnts[i] > _max ) { _max=_cnts[i]; maxIdx=i; }
      }
//      _cnts=null;
      _max=maxIdx;
    }
  }
}

class ASTRbind extends ASTUniPrefixOp {
  int argcnt;
  @Override String opStr() { return "rbind"; }
  public ASTRbind() { super(new String[]{"rbind", "ary","..."}); }
  @Override ASTOp make() { return new ASTRbind(); }
  ASTRbind parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST a;
    while( !E.isEnd() ) {
      a = E.parse();
      if( a instanceof ASTId ) {
        if (Env.staticLookup((ASTId) a) instanceof ASTFrame) dblarys.add(a);
        else
          throw new IllegalArgumentException("Could not find the frame with the identifier: " + ((ASTId)a)._id);
      } else if( a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTOp ) dblarys.add(a);
    }

    Collections.reverse(dblarys);
    argcnt=dblarys.size();
    E.eatEnd(); // eat the ending ')'
    ASTRbind res = (ASTRbind) clone();
    res._asts = dblarys.toArray(new AST[argcnt]);
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
          if( _vecs[i].get_type()==Vec.T_BAD ) continue;
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

  public static class ParallelRbinds extends H2O.H2OCountedCompleter{

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
        if (f1.vec(c).get_type() != t.vec(c).get_type() && (f1.vec(c).get_type()!=Vec.T_BAD && t.vec(c).get_type()!=Vec.T_BAD)) // allow all NA columns
          throw new IllegalArgumentException("Column type mismatch on column #"+c+"! Expected type " + get_type(f1.vec(c).get_type()) + " but vec has type " + get_type(t.vec(c).get_type()));
      }
    }
    ParallelRbinds t;
    H2O.submitTask(t =new ParallelRbinds(env, argcnt)).join();
    env.poppush(argcnt, new ValFrame(new Frame(f1.names(), t._vecs)));
  }
}

class ASTCbind extends ASTUniPrefixOp {
  int argcnt;
  boolean _deepCopy;
  @Override String opStr() { return "cbind"; }
  public ASTCbind() { super(new String[]{"cbind","ary", "deepCopy", "..."}); }
  @Override ASTOp make() {return new ASTCbind();}
  ASTCbind parse_impl(Exec E) {
    ArrayList<AST> dblarys = new ArrayList<>();
    AST a;
    a = E.parse();
    if( a instanceof ASTId ) _deepCopy = ((ASTNum)E._env.lookup((ASTId)a))._d==1;
    else throw new IllegalArgumentException("First argument of cbind must be TRUE or FALSE for the deepCopy flag.");
    while( !E.isEnd() ) {
      a = E.parse();
      if( a instanceof ASTId ) {
        if (Env.staticLookup((ASTId) a) instanceof ASTFrame) dblarys.add(a);
        else
          throw new IllegalArgumentException("Could not find the frame with the identifier: " + ((ASTId)a)._id);
      } else if( a instanceof ASTFrame || a instanceof ASTSlice || a instanceof ASTOp ) dblarys.add(a);
    }
    AST[] arys = new AST[argcnt=dblarys.size()];
    for (int i = 0; i < dblarys.size(); i++) arys[i] = dblarys.get(i);
    E.eatEnd(); // eat the ending ')'
    ASTCbind res = (ASTCbind) clone();
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
    Frame first=env.peekAryAt(0-argcnt+1);
    Frame fr = _deepCopy ? first.deepCopy(null) : new Frame(first.names(),first.vecs());
    Frame ff;
    for(int i = 1; i < argcnt; i++) {
      Frame f = env.peekAryAt(i-argcnt+1);  // Reverse order off stack
      if( fr.isCompatible(f) ) ff = _deepCopy ? f.deepCopy(null) : f;
      else                     ff = fr.makeCompatible(f);

      if (f.numCols() == 1) fr.add(f.names()[0], ff.anyVec());
      else                  fr.add(ff);
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

class ASTMedian extends ASTReducerOp {
  ASTMedian() { super( 0 ); }
  @Override String opStr() { return "median"; }
  @Override ASTOp make() { return new ASTMedian(); }
  @Override double op(double d0, double d1) { throw H2O.unimpl(); }
  @Override void apply(Env env) {
    Frame fr = env.popAry();
    Key tk=null;
    if( fr._key == null ) { DKV.put(tk=Key.make(), fr=new Frame(tk, fr.names(),fr.vecs())); }
    double median = median(fr, QuantileModel.CombineMethod.INTERPOLATE); // does linear interpolation for even sample sizes by default
    if( tk!=null ) { DKV.remove(tk); }
    env.push(new ValNum(median));
  }

  static double median(Frame fr, QuantileModel.CombineMethod combine_method) {
    if (fr.numCols() != 1) throw new IllegalArgumentException("`median` expects a single numeric column from a Frame.");
    if (!fr.anyVec().isNumeric()) throw new IllegalArgumentException("`median` expects a single numeric column from a Frame.");
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._probs = new double[]{0.5};
    parms._train = fr._key;
    parms._combine_method = combine_method;
    QuantileModel q = new Quantile(parms).trainModel().get();
    double median = q._output._quantiles[0][0];
    q.delete();
    return median;
  }
  static double median(Vec v, QuantileModel.CombineMethod combine_method) {
    Frame f = new Frame(Key.make(), null, new Vec[]{v});
    DKV.put(f);
    double res=median(f,combine_method);
    DKV.remove(f._key);
    return res;
  }
}

// the mean absolute devation
// mad = b * med(  |x_i - med(x)| )
// b = 1.4826 for normally distributed data -- but this is used anyways..
// looks like this: (h2o.mad %v #1.4826 %TRUE "interpolate")
// args are: column, constant, na.rm, combine_method
// possible combine_method values:
// lo: for even sample size, take the lo value for the median
// hi: for even sample size, take the hi value for the median
// interpolate: interpolate lo & hi
// avg: average lo & hi
class ASTMad extends ASTReducerOp {
  double _const;
  QuantileModel.CombineMethod _combine_method;
  ASTMad() { super( 0 ); }
  @Override String opStr() { return "h2o.mad"; }
  @Override ASTOp make() { return new ASTMad(); }
  @Override double op(double d0, double d1) { throw H2O.unimpl(); }
  ASTMad parse_impl(Exec E) {
    AST ary = E.parse();
    AST a = E.parse();
    // set the constant
    if( a instanceof ASTNum ) _const = ((ASTNum)a)._d;
    else throw new IllegalArgumentException("`constant` is expected to be a literal number. Got: " + a.getClass());
    a = E.parse();
    // set the narm
    if( a instanceof ASTId ) {
      AST b = E._env.lookup((ASTId)a);
      if( b instanceof ASTNum ) _narm = ((ASTNum)b)._d==1;
      else throw new IllegalArgumentException("`na.rm` is expected to be oen of %TRUE, %FALSE, %T, %F. Got: " + ((ASTId) a)._id);
    }
    // set the combine method
    _combine_method = QuantileModel.CombineMethod.valueOf(E.nextStr().toUpperCase());
    E.eatEnd();
    ASTMad res = (ASTMad)clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env e) {
    Frame f = e.popAry();
    e.push(new ValNum(mad(f,_combine_method,_const)));
  }

  static double mad(Frame f, QuantileModel.CombineMethod cm, double constant) {
    Key tk=null;
    if( f._key == null ) { DKV.put(tk=Key.make(), f=new Frame(tk, f.names(),f.vecs())); }
    final double median = ASTMedian.median(f,cm);
    Frame abs_dev = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for(int i=0;i<c._len;++i)
          nc.addNum(Math.abs(c.at8(i)-median));
      }
    }.doAll(1, f).outputFrame();
    if( abs_dev._key == null ) { DKV.put(tk=Key.make(), abs_dev=new Frame(tk, abs_dev.names(),abs_dev.vecs())); }
    double mad = ASTMedian.median(abs_dev,cm);
    DKV.remove(f._key); // drp mapping, keep vec
    DKV.remove(abs_dev._key);
    return constant*mad;
  }
}

class ASTMax extends ASTReducerOp {
  ASTMax( ) { super( Double.NEGATIVE_INFINITY); }
  @Override String opStr(){ return "max";}
  @Override ASTOp make() {return new ASTMax();}
  @Override double op(double d0, double d1) { return Math.max(d0,d1); }
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
  @Override double op(double d0, double d1) { throw H2O.unimpl(); }
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
  String _newname;
  boolean _deepCopy;
  @Override String opStr() { return "rename"; }
  ASTRename() { super(new String[] {"", "ary", "new_name", "deepCopy"}); }
  @Override ASTOp make() { return new ASTRename(); }
  ASTRename parse_impl(Exec E) {
    AST ary = E.parse();
    _newname = ((ASTString)E.parse())._s;
    AST a = E.parse();
    if( a instanceof ASTId ) _deepCopy =   ((ASTNum)E._env.lookup( ((ASTId)a) ))._d==1;
    else throw new IllegalASTException("Expected to get TRUE/FALSE for deepCopy argument");
    E.eatEnd(); // eat the ending ')'
    ASTRename res = (ASTRename) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Frame fr = e.popAry();
    if( _deepCopy ) {
      if( fr._key!=null )  // wack the old DKV mapping
        DKV.remove(fr._key);
      Frame fr2 = new Frame(Key.make(_newname),fr.names(),fr.vecs());
      DKV.put(fr2._key, fr2);
    } else {
      Frame fr2 = fr.deepCopy(_newname);
      DKV.put(fr2._key, fr2);
      e.pushAry(fr2);
    }
  }
}

// non-copying DKV put...
class ASTGPut extends ASTUniPrefixOp {
  @Override String opStr() { return "gput"; }
  ASTGPut() { super(new String[] {"lhs_name","rhs"}); }
  ASTGPut(String[] s) { super(s); }
  @Override ASTOp make() { return new ASTGPut(); }
  ASTGPut parse_impl(Exec E) {
    String l;
    if( E.isSpecial(E.peek()) ) l = E.nextStr();
    else                        l = E.parseID();
    AST lhs = new ASTString('\"',l);
    AST rhs = E.parse();
    E.eatEnd();
    ASTGPut res = (ASTGPut)clone();
    res._asts = new AST[]{rhs,lhs};
    return res;
  }
  @Override void apply(Env e) {
    // stack is [ ..., rhs, lhs ]
    Key k = Key.make(e.popStr());
    Frame fr;
    if( e.isAry() ) {
      Frame f = e.popAry();
      fr = new Frame(k, f.names(), f.vecs());
    } else if( e.isNum() ) fr = new Frame(k, null, new Vec[]{Vec.makeCon(e.popDbl(), 1)});
    else if( e.isStr() ) {
      Vec v = Vec.makeZero(1);
      v.setDomain(new String[]{e.popStr()});
      fr = new Frame(k,new String[]{"C1"}, new Vec[]{v});
    } else throw new IllegalArgumentException("Don't know what to do with: "+e.peek().getClass());
    DKV.put(k, fr);
    e.lock(fr);
    e.put(k.toString(), fr);
    e.push(new ValFrame(fr, true /*isGlobalSet*/));
  }
}

// non-copying local put...
class ASTLPut extends ASTGPut {
  @Override String opStr() { return "lput"; }
  ASTLPut() { super(new String[] {"lhs_name","rhs"}); }
  @Override ASTOp make() { return new ASTLPut(); }
  // rely on ASTGPut parse impl...
  @Override void apply(Env e) {
    // stack is [ ..., rhs, lhs ]
    Key k = Key.make(e.popStr());
    Frame fr;
    if( e.isAry() ) {
      Frame f = e.popAry();
      fr = new Frame(k, f.names(), f.vecs());
    } else if( e.isNum() ) fr = new Frame(k, null, new Vec[]{Vec.makeCon(e.popDbl(), 1)});
    else if( e.isStr() ) {
      Vec v = Vec.makeZero(1);
      v.setDomain(new String[]{e.popStr()});
      fr = new Frame(k,new String[]{"C1"}, new Vec[]{v});
    } else throw new IllegalArgumentException("Don't know what to do with: "+e.peek().getClass());
    e.lock(fr);
    e.put(k.toString(), fr);
    e.push(new ValFrame(fr, false /*isGlobalSet*/));
  }
}

class ASTSetLevel extends ASTUniPrefixOp {
  private String _lvl;
  ASTSetLevel() { super(new String[]{"setLevel", "x", "level"});}
  @Override String opStr() { return "setLevel"; }
  @Override ASTOp make() { return new ASTSetLevel(); }
  ASTSetLevel parse_impl(Exec E) {
    AST ary = E.parse();
    _lvl = ((ASTString)E.parse())._s;
    E.eatEnd(); // eat the ending ')'
    ASTSetLevel res = (ASTSetLevel) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    Frame fr = env.peekAry();
    if (fr.numCols() != 1) throw new IllegalArgumentException("`setLevel` works on a single column at a time.");
    String[] doms = fr.anyVec().domain().clone();
    if( doms == null )
      throw new IllegalArgumentException("Cannot set the level on a non-factor column!");
    final int idx = Arrays.asList(doms).indexOf(_lvl);
    if (idx == -1)
      throw new IllegalArgumentException("Did not find level `" + _lvl + "` in the column.");

    Frame fr2 = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        for (int i=0;i<c._len;++i)
          nc.addNum(idx);
      }
    }.doAll(1, fr.anyVec()).outputFrame(null, fr.names(), fr.domains());
    env.poppush(1, new ValFrame(fr2));
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
    AST a = E.parse();
    if( a instanceof ASTString ) _matches = new String[]{((ASTString)a)._s};
    else if( a instanceof ASTStringList ) _matches = ((ASTStringList)a)._s;
    else throw new IllegalArgumentException("`table` expected to be either a String or an slist. Got: " + a.getClass());
    Arrays.sort(_matches);

    // `nomatch` is just a number in case no match
    AST nm = E.parse();
    if( nm instanceof ASTNum ) _nomatch = ((ASTNum)nm)._d;
    else throw new IllegalArgumentException("Argument `nomatch` expected a number. Got: " + nm.getClass());

    // drop the incomparables arg for now ...
    AST incomp = E.parse();

    E.eatEnd(); // eat the ending ')'
    ASTMatch res = (ASTMatch) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Frame fr = e.popAry();
    if (fr.numCols() != 1 && !fr.anyVec().isEnum()) throw new IllegalArgumentException("can only match on a single categorical column.");
    Key tmp = Key.make();
    final String[] matches = _matches;
    Frame rez = new MRTask() {
      @Override public void map(Chunk c, NewChunk n) {
        int rows = c._len;
        for (int r = 0; r < rows; ++r) n.addNum(in(matches, c.vec().domain()[(int)c.at8(r)]));
      }
    }.doAll(1, fr.anyVec()).outputFrame(tmp, null, null);
    e.pushAry(rez);
  }
  private static int in(String[] matches, String s) { return Arrays.binarySearch(matches, s) >=0 ? 1: 0;}
}

// R like binary operator ||
class ASTOR extends ASTBinOp {
  @Override String opStr() { return "||"; }
  ASTOR( ) { super(); }
  @Override double op(double d0, double d1) { throw H2O.unimpl(); }
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
    if (Double.isNaN(op2))         env.poppush(2, new ValNum(op2));
    // both 0 ? push False
    else if (op1 == 0 && op2 == 0) env.poppush(2, new ValNum(0.0));
    // else push True
    else                           env.poppush(2, new ValNum(1.0));
  }
}

// Similar to R's seq_len
class ASTSeqLen extends ASTUniPrefixOp {
  double _length;
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
    E.eatEnd(); // eat the ending ')'
    ASTSeqLen res = (ASTSeqLen) clone();
    res._asts = new AST[]{};
    return res;
  }

  @Override void apply(Env env) {
    int len = (int) Math.ceil(_length);
    if (len <= 0)
      throw new IllegalArgumentException("Error in seq_len(" +len+"): argument must be coercible to positive integer");
    Frame fr = new Frame(new String[]{"c"}, new Vec[]{Vec.makeSeq(len,true)});
    env.pushAry(fr);
  }
}

// Same logic as R's generic seq method
class ASTSeq extends ASTUniPrefixOp {
  double _from;
  double _to;
  double _by;

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

    if( _from >= _to ) throw new IllegalArgumentException("`from` >= `to`: " + _from + ">=" + _to);
    if( _by <= 0 ) throw new IllegalArgumentException("`by` must be >0: " + _by + " <=0");

    E.eatEnd(); // eat the ending ')'
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

class ASTSetDomain extends ASTUniPrefixOp {
  String[] _domains;
  @Override String opStr() { return "setDomain"; }
  public ASTSetDomain() { super(new String[]{"setDomain", "x", "slist"}); }
  @Override ASTOp make() { return new ASTSetDomain(); }
  ASTSetDomain parse_impl(Exec E) {
    AST ary = E.parse();
    AST a = E.parse();
    if( a instanceof ASTStringList ) _domains = ((ASTStringList)a)._s;
    else if( a instanceof ASTNull  ) _domains = null;
    else throw new IllegalArgumentException("domains expected to an array of strings. Got :" + a.getClass());
    ASTSetDomain res = (ASTSetDomain)clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {
    // pop frame, should be a single ENUM Vec
    // push in new domain
    // DKV put the updated vec
    // no stack pop or push
    Frame f = env.peekAry();
    if( f.numCols()!=1 ) throw new IllegalArgumentException("Must be a single column. Got: " + f.numCols() + " columns.");
    Vec v = f.anyVec();
    if( !v.isEnum() ) throw new IllegalArgumentException("Vector must be a factor column. Got: "+v.get_type_str());
    if( _domains!=null && _domains.length != v.domain().length)
      throw new IllegalArgumentException("Number of replacement factors must equal current number of levels. Current number of levels: " + v.domain().length + " != " + _domains.length);
    v.setDomain(_domains);
    DKV.put(v);
  }
}

class ASTRepLen extends ASTUniPrefixOp {
  double _length;
  @Override String opStr() { return "rep_len"; }
  public ASTRepLen() { super(new String[]{"rep_len", "x", "length.out"}); }
  @Override ASTOp make() { return new ASTRepLen(); }
  ASTRepLen parse_impl(Exec E) {
    AST ary = E.parse();
    AST a = E.parse();
    _length = a.treeWalk(new Env(new HashSet<Key>())).popDbl();
    E.eatEnd(); // eat the ending ')'
    ASTRepLen res = (ASTRepLen) clone();
    res._asts = new AST[]{ary};
    return res;
  }
  @Override void apply(Env env) {

    // two cases if x is a frame: x is a single vec, x is a list of vecs
    if (env.isAry()) {
      final Frame fr = env.popAry();
      if (fr.numCols() == 1) {

        // In this case, create a new vec of length _length using the elements of x
        Vec v = Vec.makeRepSeq((long)_length, (fr.numRows()));  // vec of "indices" corresponding to rows in x
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

class ASTHist extends ASTUniPrefixOp {
  @Override String opStr() { return "hist"; }
  public ASTHist() { super(new String[]{"hist", "x", "breaks"}); }
  @Override ASTHist make() { return new ASTHist(); }
  ASTHist parse_impl(Exec E) {
    AST ary = E.parse();
    AST breaks = E.parse();
    ASTHist res = (ASTHist)clone();
    res._asts = new AST[]{ary,breaks};
    return res;
  }
  @Override void apply(Env e) {
    // stack is [ ..., ary, breaks]
    // handle the breaks
    Frame fr2;
    Val v = e.pop(); // must be a dlist, string, number
    String algo=null;
    int numBreaks=-1;
    double[] breaks=null;

    if( v instanceof ValStr )             algo      = ((ValStr)v)._s.toLowerCase();
    else if( v instanceof ValDoubleList ) breaks    = ((ValDoubleList)v)._d;
    else if( v instanceof ValNum )        numBreaks = (int)((ValNum)v)._d;
    else if( v instanceof ValLongList   ) {
      long[] breaksLong = ((ValLongList)v)._l;
      breaks = new double[breaksLong.length];
      int i=0;
      for(long l:breaksLong) breaks[i++]=l;
    } else throw new IllegalArgumentException("breaks must be a string, a list of doubles, or a number. Got: " + v.getClass());

    Frame f = e.popAry();
    if( f.numCols() != 1) throw new IllegalArgumentException("Hist only applies to single numeric columns.");
    Vec vec = f.anyVec();
    if( !vec.isNumeric() )throw new IllegalArgumentException("Hist only applies to single numeric columns.");


    HistTask t;
    double h;
    double x1=vec.max();
    double x0=vec.min();
    if( breaks != null ) t = new HistTask(breaks,-1,-1/*ignored if _h==-1*/).doAll(vec);
    else if( algo!=null ) {
      switch (algo) {
        case "sturges": numBreaks = sturges(vec); h=(x1-x0)/numBreaks; break;
        case "rice":    numBreaks = rice(vec);    h=(x1-x0)/numBreaks; break;
        case "sqrt":    numBreaks = sqrt(vec);    h=(x1-x0)/numBreaks; break;
        case "doane":   numBreaks = doane(vec);   h=(x1-x0)/numBreaks; break;
        case "scott":   h=scotts_h(vec); numBreaks = scott(vec,h);     break;  // special bin width computation
        case "fd":      h=fds_h(vec);    numBreaks = fd(vec, h);       break;  // special bin width computation
        default:        numBreaks = sturges(vec); h=(x1-x0)/numBreaks;         // just do sturges even if junk passed in
      }
      t = new HistTask(computeCuts(vec,numBreaks),h,x0).doAll(vec);
    }
    else {
      h = (x1-x0)/numBreaks;
      t = new HistTask(computeCuts(vec,numBreaks),h,x0).doAll(vec);
    }
    // wanna make a new frame here [breaks,counts,mids]
    final double[] brks=t._breaks;
    final long  [] cnts=t._counts;
    final double[] mids_true=t._mids;
    final double[] mids = new double[t._breaks.length-1];
    for(int i=1;i<brks.length;++i) mids[i-1] = .5*(t._breaks[i-1]+t._breaks[i]);
    Vec layoutVec = Vec.makeZero(brks.length);
    fr2 = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk[] nc) {
        int start = (int)c[0].start();
        for(int i=0;i<c[0]._len;++i) {
          nc[0].addNum(brks[i+start]);
          if(i==0) {
            nc[1].addNA();
            nc[2].addNA();
            nc[3].addNA();
          } else {
            nc[1].addNum(cnts[(i-1)+start]);
            nc[2].addNum(mids_true[(i-1)+start]);
            nc[3].addNum(mids[(i-1)+start]);
          }
        }
      }
    }.doAll(4, layoutVec).outputFrame(null, new String[]{"breaks", "counts", "mids_true", "mids"},null);
    layoutVec.remove();
    e.pushAry(fr2);
  }

  private static int sturges(Vec v) { return (int)Math.ceil( 1 + log2(v.length()) ); }
  private static int rice   (Vec v) { return (int)Math.ceil( 2*Math.pow(v.length(),1./3.)); }
  private static int sqrt   (Vec v) { return (int)Math.sqrt(v.length()); }
  private static int doane  (Vec v) { return (int)(1 + log2(v.length()) + log2(1+ (Math.abs(third_moment(v)) / sigma_g1(v))) );  }
  private static int scott  (Vec v, double h) { return (int)Math.ceil((v.max()-v.min()) / h); }
  private static int fd     (Vec v, double h) { return (int)Math.ceil((v.max() - v.min()) / h); }   // Freedman-Diaconis slightly modified to use MAD instead of IQR
  private static double fds_h(Vec v) { return 2*ASTMad.mad(new Frame(v), null, 1.4826)*Math.pow(v.length(),-1./3.); }
  private static double scotts_h(Vec v) { return 3.5*Math.sqrt(ASTVar.getVar(v,true)) / (Math.pow(v.length(),1./3.)); }
  private static double log2(double numerator) { return (Math.log(numerator))/Math.log(2)+1e-10; }
  private static double sigma_g1(Vec v) { return Math.sqrt( (6*(v.length()-2)) / ((v.length()+1)*(v.length()+3)) ); }
  private static double third_moment(Vec v) {
    final double mean = ASTVar.getMean(v,true,"");
    ThirdMomTask t = new ThirdMomTask(mean).doAll(v);
    double m2 = t._ss / v.length();
    double m3 = t._sc / v.length();
    return m3 / Math.pow(m2, 1.5);
  }

  private static class ThirdMomTask extends MRTask<ThirdMomTask> {
    double _ss;
    double _sc;
    final double _mean;
    ThirdMomTask(double mean) { _mean=mean; }
    @Override public void setupLocal() { _ss=0;_sc=0; }
    @Override public void map(Chunk c) {
      for( int i=0;i<c._len;++i ) {
        if( !c.isNA(i) ) {
          double d = c.atd(i) - _mean;
          double d2 = d*d;
          _ss+= d2;
          _sc+= d2*d;
        }
      }
    }
    @Override public void reduce(ThirdMomTask t) { _ss+=t._ss; _sc+=t._sc; }
  }

  private double[] computeCuts(Vec v, int numBreaks) {
    if( numBreaks <= 0 ) throw new IllegalArgumentException("breaks must be a positive number");
    // just make numBreaks cuts equidistant from each other spanning range of [v.min, v.max]
    double min;
    double w = ( v.max() - (min=v.min()) ) / numBreaks;
    double[] res= new double[numBreaks];
    for( int i=0;i<numBreaks;++i ) res[i] = min + w * (i+1);
    return res;
  }

  private static class HistTask extends MRTask<HistTask> {
    final private double _h;      // bin width
    final private double _x0;     // far left bin edge
    final private double[] _min;  // min for each bin, updated atomically
    final private double[] _max;  // max for each bin, updated atomically
    // unsafe crap for mins/maxs of bins
    private static final Unsafe U = UtilUnsafe.getUnsafe();
    // double[] offset and scale
    private static final int _dB = U.arrayBaseOffset(double[].class);
    private static final int _dS = U.arrayIndexScale(double[].class);
    private static long doubleRawIdx(int i) { return _dB + _dS * i; }
    // long[] offset and scale
    private static final int _8B = U.arrayBaseOffset(long[].class);
    private static final int _8S = U.arrayIndexScale(long[].class);
    private static long longRawIdx(int i)   { return _8B + _8S * i; }

    // out
    private final double[] _breaks;
    private final long  [] _counts;
    private final double[] _mids;

    HistTask(double[] cuts, double h, double x0) {
      _breaks=cuts;
      _min=new double[_breaks.length-1];
      _max=new double[_breaks.length-1];
      _counts=new long[_breaks.length-1];
      _mids=new double[_breaks.length-1];
      _h=h;
      _x0=x0;
    }
    @Override public void map(Chunk c) {
      // if _h==-1, then don't have fixed bin widths... must loop over bins to obtain the correct bin #
      for( int i = 0; i < c._len; ++i ) {
        int x=1;
        if( c.isNA(i) ) continue;
        double r = c.atd(i);
        if( _h==-1 ) {
          for(; x < _counts.length; x++)
            if( r <= _breaks[x] ) break;
          x--; // back into the bin where count should go
        } else
          x = Math.min( _counts.length-1, (int)Math.floor( (r-_x0) / _h ) );     // Pick the bin   floor( (x - x0) / h ) or ceil( (x-x0)/h - 1 ), choose the first since fewer ops!
        bumpCount(x);
        setMinMax(Double.doubleToRawLongBits(r),x);
      }
    }
    @Override public void reduce(HistTask t) {
      if(_counts!=t._counts) ArrayUtils.add(_counts,t._counts);
      for(int i=0;i<_mids.length;++i) {
        _min[i] = t._min[i] < _min[i] ? t._min[i] : _min[i];
        _max[i] = t._max[i] > _max[i] ? t._max[i] : _max[i];
      }
    }
    @Override public void postGlobal() { for(int i=0;i<_mids.length;++i) _mids[i] = 0.5*(_max[i] + _min[i]); }

    private void bumpCount(int x) {
      long o = _counts[x];
      while(!U.compareAndSwapLong(_counts,longRawIdx(x),o,o+1))
        o=_counts[x];
    }
    private void setMinMax(long v, int x) {
      double o = _min[x];
      double vv = Double.longBitsToDouble(v);
      while( vv < o && U.compareAndSwapLong(_min,doubleRawIdx(x),Double.doubleToRawLongBits(o),v))
        o = _min[x];
      setMax(v,x);
    }
    private void setMax(long v, int x) {
      double o = _max[x];
      double vv = Double.longBitsToDouble(v);
      while( vv > o && U.compareAndSwapLong(_min,doubleRawIdx(x),Double.doubleToRawLongBits(o),v))
        o = _max[x];
    }
  }
}

// Compute exact quantiles given a set of cutoffs, using multipass binning algo.
class ASTQtile extends ASTUniPrefixOp {
  double[] _probs = null;  // if probs is null, pop the _probs frame etc.
  QuantileModel.CombineMethod _combine_method  = null;
  @Override String opStr() { return "quantile"; }
  public ASTQtile() { super(new String[]{"quantile","x","probs","combine_method"}); }
  @Override ASTQtile make() { return new ASTQtile(); }
  @Override ASTQtile parse_impl(Exec E) {
    // Get the ary
    AST ary = E.parse();
    // parse the probs, either a ASTSeries or an ASTSeq -> resulting in a Frame _ONLY_
    AST seq = E.parse();
    if( seq instanceof ASTDoubleList ) { _probs = ((ASTDoubleList)seq)._d; seq=null; }
    else                               _probs = null;
    _combine_method = QuantileModel.CombineMethod.valueOf(E.nextStr().toUpperCase());
    E.eatEnd(); // eat the ending ')'
    ASTQtile res = (ASTQtile) clone();
    res._asts = seq == null ? new AST[]{ary} : new AST[]{ary, seq};
    return res;
  }

  @Override void apply(Env env) {
    QuantileModel.QuantileParameters parms = new QuantileModel.QuantileParameters();
    parms._probs = _probs;
    if( _probs == null ) {
      final Frame probs;
      if( env.isAry() ) {
        probs = env.popAry();
        if( probs.numCols() != 1 ) throw new IllegalArgumentException("Probs must be a single vector.");
        Vec pv = probs.anyVec();
        double[] p = parms._probs = new double[(int)pv.length()];
        for( int i = 0; i < pv.length(); i++)
          if ((p[i] = pv.at((long) i)) < 0 || p[i] > 1)
            throw new IllegalArgumentException("Quantile: probs must be in the range of [0, 1].");
      } else if( env.isNum() ) {
        double p[] = parms._probs = new double[1];
        p[0] = env.popDbl();
        if (p[0] <0 || p[0] > 1)
          throw new IllegalArgumentException("Quantile: probs must be in the range of [0, 1].");
      }
    }
    parms._combine_method = _combine_method;
    Frame x = env.popAry();
    Key tk=null;
    if( x._key == null ) { DKV.put(tk=Key.make(), x=new Frame(tk, x.names(),x.vecs())); }
    parms._train = x._key;
    QuantileModel q = new Quantile(parms).trainModel().get();
    if( tk!=null ) { DKV.remove(tk); }
    Vec shape = Vec.makeZero(parms._probs.length);
    Key[] keys = shape.group().addVecs(1 /*1 more for the probs themselves*/ + x.numCols());
    Vec[] vecs = new Vec[keys.length];
    String[] names = new String[keys.length];
    vecs [0] = Vec.makeCon(keys[0],parms._probs);
    DKV.put(keys[0],vecs[0]);
    names[0] = "Probs";
    for( int i=1; i<=x.numCols(); ++i ) {
      vecs[i] = Vec.makeCon(keys[i],q._output._quantiles[i-1]);
      DKV.put(keys[i],vecs[i]);
      names[i] = x._names[i-1]+"Quantiles";
    }
    Frame fr = new Frame(names,vecs);
    q.delete();
    shape.remove();
    parms._probs=_probs=null;
    env.pushAry(fr);
  }
}

class ASTSetColNames extends ASTUniPrefixOp {
  long[] _idxs;
  String[] _names;
  @Override String opStr() { return "colnames="; }
  public ASTSetColNames() { super(new String[]{}); }
  @Override ASTSetColNames make() { return new ASTSetColNames(); }

  // AST: (colnames<- $ary {indices} {names})
  // example:  (colnames<- $iris {#3;#5} {new_name1;new_name2})
  // also acceptable: (colnames<- $iris (: #3 #5) {new_name1;new_name2})
  @Override ASTSetColNames parse_impl(Exec E) {
    // frame we're changing column names of
    AST ary = E.parse();
    // col ids: can be a (: # #) or (llist # # #) or #
    AST cols = E.parse();
    if( cols instanceof ASTSpan )          _idxs = ((ASTSpan)cols).toArray();
    else if( cols instanceof ASTLongList ) _idxs = ((ASTLongList)cols)._l;
    else if( cols instanceof ASTDoubleList) {
      double[] d = ((ASTDoubleList)cols)._d;
      _idxs=new long[d.length];
      int i=0;
      for(double dd:d) _idxs[i++] = (long)dd;
    }
    else if( cols instanceof ASTNum )      _idxs = new long[]{(long)((ASTNum) cols).dbl()};
    else throw new IllegalArgumentException("Bad AST: Expected a span, llist, or number for the column indices. Got: " + cols.getClass());

    AST names = E.parse();
    // names can be: (slist "" "" "") or ""
    if( names instanceof ASTStringList ) _names = ((ASTStringList)names)._s;
    else if( names instanceof ASTString) _names = new String[]{((ASTString)names)._s};
    else if( names instanceof ASTFrame ) _names = new String[]{((ASTFrame)names)._key};
    else throw new IllegalArgumentException("Bad AST: Expected slist or string for column names. Got: " + names.getClass());

    if (_names.length != _idxs.length)
      throw new IllegalArgumentException("Mismatch! Number of columns to change ("+_idxs.length+") does not match number of names given ("+_names.length+").");

    E.eatEnd(); // eat the ending ')'
    ASTSetColNames res = (ASTSetColNames)clone();
    res._asts = new AST[]{ary};
    res._idxs = _idxs; res._names = _names;
    return res;
  }

  @Override void apply(Env env) {
    try {
      Frame f = env.popAry();
      for (int i = 0; i < _names.length; ++i)
        f._names[(int) _idxs[i]] = _names[i];
      if (f._key != null && DKV.get(f._key) != null) DKV.put(f);
      env.pushAry(f);
    } catch (ArrayIndexOutOfBoundsException e) {
      Log.info("AIOOBE!!! _idxs.length="+_idxs.length+ "; _names.length="+_names.length);
      throw e; //rethrow
    }
  }
}

// Remove a frame key and NOT the internal Vecs.
// Used by Python which tracks Vecs independently from Frames
class ASTRemoveFrame extends ASTUniPrefixOp {
  String _newname;
  @Override String opStr() { return "removeframe"; }
  ASTRemoveFrame() { super(new String[] {"", "ary"}); }
  @Override ASTOp make() { return new ASTRemoveFrame(); }
  ASTRemoveFrame parse_impl(Exec E) {
    AST ary = E.parse();
    E.eatEnd(); // eat the ending ')'
    ASTRemoveFrame res = (ASTRemoveFrame) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    Val v = e.pop();
    Frame fr;
    if( v instanceof ValFrame ) {
      fr = ((ValFrame) v)._fr;
      fr.restructure(new String[0], new Vec[0]);
      assert fr.keys().length==0 : "Restructiring the frame failed in removeFrame";
      fr.remove();
    }
    e.push(new ValNull());
  }
}

//remove vecs by index&frame lookup. push subset frame onto stack
class ASTRemoveVecs extends ASTUniPrefixOp {
  long[] _rmVecs;
  @Override String opStr() { return "removeVecs"; }
  ASTRemoveVecs() { super(new String[] {"", "ary", "llist"}); }
  @Override ASTOp make() { return new ASTRemoveVecs(); }
  ASTRemoveVecs parse_impl(Exec E) {
    AST ary = E.parse();
    AST a = E.parse();
    if( a instanceof ASTLongList ) _rmVecs = ((ASTLongList)a)._l;
    else if( a instanceof ASTNum ) _rmVecs = new long[]{(long)((ASTNum)a)._d};
    else throw new IllegalArgumentException("Expected to get an `llist` or `num`. Got: " + a.getClass());
    E.eatEnd(); // eat the ending ')'
    ASTRemoveVecs res = (ASTRemoveVecs) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void apply(Env e) {
    int[] idxs = new int[_rmVecs.length];
    int i=0;
    for(long l:_rmVecs) idxs[i++]=(int)l;
    Frame fr = e.popAry();
    for(Vec v:fr.remove(idxs)) v.remove(); // a little inefficeint... each vec blocks 'til it's gone.
    DKV.put(fr._key, fr);
    e.pushAry(fr);
  }
}

class ASTRunif extends ASTUniPrefixOp {
  long   _seed;
  @Override String opStr() { return "h2o.runif"; }
  public ASTRunif() { super(new String[]{"h2o.runif","dbls","seed"}); }
  @Override ASTOp make() {return new ASTRunif();}
  @Override ASTRunif parse_impl(Exec E) {
    // peel off the ary
    AST ary = E.parse();
    // parse the seed
    _seed = (long) E.parse().treeWalk(new Env(new HashSet<Key>())).popDbl();
    E.eatEnd(); // eat the ending ')'
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
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.parse());
    _narm = ((ASTNum)a).dbl() == 1;
    E.eatEnd(); // eat the ending ')'
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
    // Get the trim
    AST y = E.parse();
    if( y instanceof ASTNull ) { _ynull=true; y=ary; }
    // Get the na.rm
    AST a = E._env.lookup((ASTId)E.parse());
    try {
      _narm = ((ASTNum) a).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `na.rm` expected to be a number.");
    }
    // Get the `use`
    ASTString use;
    AST u = E.parse();
    if( u instanceof ASTNull ) use = new ASTString('\"', "null");
    else if( u instanceof ASTString ) use = (ASTString)u;
    else throw new IllegalArgumentException("Argument `use` expected to be a string.");

    E.eatEnd(); // eat the ending ')'
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
      Frame y;
      String use;
      if( env.isEmpty() || env.sp() <= 1 ) { y=fr; use="everything"; }
      else {
                            y = ((ValFrame) env.peekAt(-1))._fr;  // number of columns
        if( env.isEmpty() || env.sp() <= 1 ) use = "everything";
        else                use = ((ValStr) env.peekAt(-2))._s;  // what to do w/ NAs: "everything","all.obs","complete.obs","na.or.complete","pairwise.complete.obs"
      }
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
        env.poppush(3, new ValNum(Double.isNaN(ss) ? ss : ss/divideby));

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
          Futures fs = new Futures();
          for (int i = 0; i < covars.length; i++) {
            AppendableVec v = new AppendableVec(keys[i]);
            NewChunk c = new NewChunk(v, 0);
            for (int j = 0; j < covars[0].length; j++) c.addNum(covars[i][j]);
            c.close(0, fs);
            vecs[i] = v.close(fs);
          }
          fs.blockForPending();
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
    E.eatEnd(); // eat the ending ')'
    // Finish the rest
    ASTMean res = (ASTMean) clone();
    res._asts = new AST[]{ary};
    return res;
  }

  @Override void exec(Env e, AST[] args) {
    args[0].exec(e);
    e.put(Key.make().toString(), e.peekAry());
    if (args != null) {
      if (args.length > 3) throw new IllegalArgumentException("Too many arguments passed to `mean`");
      for(int i=1;i<args.length;++i) {
        AST a = args[i];
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
      double rows=0;
      for(Vec v : fr.vecs()) {
        double val = v.at(0);
        if( !Double.isNaN(val)) {mean += v.at(0); rows++;}
      }
      env.push(new ValNum(mean/rows));
    } else {
      Vec v = fr.anyVec();
      if( _narm || v.naCnt()==0 ) env.push(new ValNum(v.mean()));
      else {
        MeanNARMTask t = new MeanNARMTask(false).doAll(v);
        if (t._rowcnt == 0 || Double.isNaN(t._sum)) env.push(new ValNum(Double.NaN));
        else env.push(new ValNum(t._sum / t._rowcnt));
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
    AST two = E.parse();
    if (two instanceof ASTString) two = new ASTNull();
    E.eatEnd(); // eat the ending ')'
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
      else if (one.numCols() < 1 || one.numCols() > 2)
        throw new IllegalArgumentException("`table` supports at *most* two vectors and at least one vector.");

    Frame fr;
    if (two != null) fr = new Frame(one.add(two));
    else fr = one;

    final int ncol;
    if ((ncol = fr.vecs().length) > 2)
      throw new IllegalArgumentException("table does not apply to more than two cols.");
    Vec dataLayoutVec;
    Frame fr2;
    String colnames[];
    String[][] d = new String[ncol+1][];

    if (ncol == 1 && fr.anyVec().isInt()) {  // fast path for int vecs
      final int min = (int) fr.anyVec().min();
      final int max = (int) fr.anyVec().max();
      colnames = new String[]{fr.name(0), "Count"};
      d[0] = fr.anyVec().domain(); // should always be null for all neg values!
      d[1] = null;

      // all pos
      if (min >= 0) {
        UniqueColumnCountTask t = new UniqueColumnCountTask(max, false, false, 0).doAll(fr.anyVec());
        final long[] cts = t._cts;
        dataLayoutVec = Vec.makeCon(0, cts.length);

        // second pass to build the result frame
        fr2 = new MRTask() {
          @Override
          public void map(Chunk[] c, NewChunk[] cs) {
            for (int i = 0; i < c[0]._len; ++i) {
              int idx = (int) (i + c[0].start());
              if (cts[idx] == 0) continue;
              cs[0].addNum(idx);
              cs[1].addNum(cts[idx]);
            }
          }
        }.doAll(2, dataLayoutVec).outputFrame(colnames, d);

        // all neg  -- flip the sign and count...
      } else if (min <= 0 && max <= 0) {
        UniqueColumnCountTask t = new UniqueColumnCountTask(-1 * min, true, false, 0).doAll(fr.anyVec());
        final long[] cts = t._cts;
        dataLayoutVec = Vec.makeCon(0, cts.length);

        // second pass to build the result frame
        fr2 = new MRTask() {
          @Override
          public void map(Chunk[] c, NewChunk[] cs) {
            for (int i = 0; i < c[0]._len; ++i) {
              int idx = (int) (i + c[0].start());
              if (cts[idx] == 0) continue;
              cs[0].addNum(idx * -1);
              cs[1].addNum(cts[idx]);
            }
          }
        }.doAll(2, dataLayoutVec).outputFrame(colnames, d);

        // mixed
      } else {
        UniqueColumnCountTask t = new UniqueColumnCountTask(max + -1 * min, false, true, max).doAll(fr.anyVec()); // pivot around max value... vals > max are negative
        final long[] cts = t._cts;
        dataLayoutVec = Vec.makeCon(0, cts.length);

        // second pass to build the result frame
        fr2 = new MRTask() {
          @Override
          public void map(Chunk[] c, NewChunk[] cs) {
            for (int i = 0; i < c[0]._len; ++i) {
              int idx = (int) (i + c[0].start());
              if (cts[idx] == 0) continue;
              cs[0].addNum(idx > max ? (idx - max) * -1 : idx);
              cs[1].addNum(cts[idx]);
            }
          }
        }.doAll(2, dataLayoutVec).outputFrame(colnames, d);
      }
    } else {
      // Build a NBHS of all groups
      // Create a dense array (1 index per group) of counts, updated atomically
      colnames = ncol==1? new String[]{fr.name(0), "count"} : new String[]{fr.name(0), fr.name(1), "count"};
      long s = System.currentTimeMillis();

      Uniq2ColTsk u = new Uniq2ColTsk().doAll(fr);
      Log.info("Finished gathering uniq groups in: " + (System.currentTimeMillis() - s) / 1000. + " (s)");

      final ASTddply.Group[] pairs = u._s._g.toArray(new ASTddply.Group[u._s.size()]);
      dataLayoutVec = Vec.makeCon(0, pairs.length);

      s = System.currentTimeMillis();
      NewHashMap h = new NewHashMap(pairs).doAll(dataLayoutVec);
      Log.info("Finished creating new HashMap in: " + (System.currentTimeMillis() - s) / 1000. + " (s)");

      s = System.currentTimeMillis();
      final long[] cnts = new CountUniq2ColTsk(h._s).doAll(fr)._cnts;
      Log.info("Finished gathering counts in: " + (System.currentTimeMillis() - s) / 1000. + " (s)");

      d[0] = fr.vec(0).domain();
      if( ncol==2 ) d[1] = fr.vec(1).domain();

      fr2 = new MRTask() {
        @Override
        public void map(Chunk[] c, NewChunk[] cs) {
          int start = (int)c[0].start();
          for (int i = 0; i < c[0]._len; ++i) {
            double[] g = pairs[i+start]._ds;
            cs[0].addNum(g[0]);
            if( ncol==2) {
              cs[1].addNum(g[1]);
              cs[2].addNum(cnts[i+start]);
            } else {
              cs[1].addNum(cnts[i+start]);
            }
          }
        }
      }.doAll(ncol + 1, dataLayoutVec).outputFrame(colnames, d);
    }
    Keyed.remove(dataLayoutVec._key);
    env.pushAry(fr2);
  }

  // gets vast majority of cases and is stupidly fast (35x faster than using UniqueTwoColumnTask)
  public static class UniqueColumnCountTask extends MRTask<UniqueColumnCountTask> {
    long[] _cts;
    final int _max;
    final boolean _flip;
    final boolean _mixed;
    final int _piv;
    public UniqueColumnCountTask(int max, boolean flip, boolean mixed, int piv) { _max = max; _flip = flip; _mixed = mixed; _piv = piv; }
    @Override public void map( Chunk c ) {
      _cts = MemoryManager.malloc8(_max+1);
      // choose the right hot loop
      if (_flip) {
        for (int i = 0; i < c._len; ++i) {
          if (c.isNA(i)) continue;
          int val = (int) (-1 * c.at8(i));
          _cts[val]++;
        }
      } else if (_mixed) {
        for (int i = 0; i < c._len; ++i) {
          if (c.isNA(i)) continue;
          int val = (int) (c.at8(i));
          int idx = val < 0 ? -1*val + _piv : val;
          _cts[idx]++;
        }
      } else {
        for (int i = 0; i < c._len; ++i) {
          if (c.isNA(i)) continue;
          int val = (int) (c.at8(i));
          _cts[val]++;
        }
      }
    }
    @Override public void reduce(UniqueColumnCountTask t) { ArrayUtils.add(_cts, t._cts); }
  }

  private static class Uniq2ColTsk extends MRTask<Uniq2ColTsk> {
    ASTGroupBy.IcedNBHS<ASTddply.Group> _s;
    private long[] _cols;
    @Override public void setupLocal() {
      _s = new ASTGroupBy.IcedNBHS<>();
      _cols = new long[_fr.numCols()];
      for(int i=0;i<_cols.length;++i) _cols[i]=i;
    }
    @Override public void map(Chunk[] c) {
      ASTddply.Group g = new ASTddply.Group(_cols.length);
      for (int i=0;i<c[0]._len;++i)
        if( _s.add((ASTddply.Group)g.fill(i,c,_cols))) {
          g = new ASTddply.Group(_cols.length);
        }
    }
    @Override public void reduce(Uniq2ColTsk t) { if (_s!=t._s) _s.addAll(t._s._g); }
  }

  private static class NewHashMap extends MRTask<NewHashMap> {
    IcedHM<ASTddply.Group, Integer> _s;
    ASTddply.Group[] _m;
    NewHashMap(ASTddply.Group[] m) { _m = m; }
    @Override public void setupLocal() { _s = new IcedHM<>();}
    @Override public void map(Chunk[] c) {
      int start = (int)c[0].start();
      for (int i = 0; i < c[0]._len; ++i)
        _s.put(_m[i + start], i+start);
    }
    @Override public void reduce(NewHashMap t) { if (_s != t._s) _s.putAll(t._s); }
  }

  private static class CountUniq2ColTsk extends MRTask<CountUniq2ColTsk> {
    private static final Unsafe _unsafe = UtilUnsafe.getUnsafe();
    IcedHM<ASTddply.Group, Integer> _m;
    private long[] _cols;
    // out
    long[] _cnts;
    private static final int _b = _unsafe.arrayBaseOffset(long[].class);
    private static final int _s = _unsafe.arrayIndexScale(long[].class);
    private static long ssid(int i) { return _b + _s*i; } // Scale and Shift

    CountUniq2ColTsk(IcedHM<ASTddply.Group, Integer> s) {_m = s; }
    @Override public void setupLocal() {
      _cnts = MemoryManager.malloc8(_m.size());
      _cols = new long[_fr.numCols()];
      for(int i=0;i<_cols.length;++i) _cols[i]=i;
    }
    @Override public void map(Chunk[] cs) {
      ASTddply.Group g = new ASTddply.Group(_cols.length);
      for (int i=0; i < cs[0]._len; ++i) {
        int h = _m.get((ASTddply.Group)g.fill(i,cs,_cols));
        long offset = ssid(h);
        long c = _cnts[h];
        while(!_unsafe.compareAndSwapLong(_cnts,offset,c,c+1))  //yee-haw
          c = _cnts[h];
      }
    }
    @Override public void reduce(CountUniq2ColTsk t) { if (_cnts != t._cnts) ArrayUtils.add(_cnts, t._cnts); }
  }

  // custom serializer for <Group,Int> pairs
  private static class IcedHM<Group extends ASTddply.Group,Int extends Integer> extends Iced {
    private NonBlockingHashMap<Group,Integer> _m; // the nbhm to (de)ser
    IcedHM() { _m = new NonBlockingHashMap<>(); }
    void put(Group g, Int i) { _m.put(g,i);}
    void putAll(IcedHM<Group,Int> m) {_m.putAll(m._m);}
    int size() { return _m.size(); }
    int get(Group g) { return _m.get(g); }
    @Override public AutoBuffer write_impl(AutoBuffer ab) {
      if( _m==null || _m.size()==0 ) return ab.put4(0);
        else {
          ab.put4(_m.size());
          for(Group g:_m.keySet()) { ab.put(g); ab.put4(_m.get(g)); }
        }
      return ab;
    }
    @Override public IcedHM read_impl(AutoBuffer ab) {
      int mLen;
      if( (mLen=ab.get4())!=0 ) {
        _m = new NonBlockingHashMap<>();
        for( int i=0;i<mLen;++i ) _m.put((Group)ab.get(), ab.get4());
      }
      return this;
    }
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
    AST yes = E.parse(); // could be num
    AST no  = E.parse(); // could be num
    E.eatEnd(); // eat the ending ')'
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
        throw new H2OIllegalArgumentException("Bad expression Rapids: " + sb.toString(), "Bad expression Rapids: " + sb.toString() + "; exception: " + e.toString());
      } finally {
        if (env!=null)env.unlock();
      }
      //      if (env != null) env.cleanup(ret==res?null:res, (Frame)DKV.remove(k).get());
      return tgt.makeCompatible(res);
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
    Frame tst;
    if( env.isNum() ) { tst = new Frame(new String[]{"tst"},new Vec[]{Vec.makeCon(env.popDbl(), 1)}); }
    else if (!env.isAry()) throw new IllegalArgumentException("`test` argument must be a frame: ifelse(`test`, `yes`, `no`)");
    else tst = env.popAry();
    if (tst.numCols() != 1)
      throw new IllegalArgumentException("`test` has "+tst.numCols()+" columns. `test` must have exactly 1 column.");
    Frame yes=null; double dyes=0;
    Frame no=null; double dno=0;
    if (env.isAry()) yes = env.popAry(); else dyes = env.popDbl();
    if (env.isAry()) no  = env.popAry(); else dno  = env.popDbl();

    if (yes != null && no != null) {
      if (yes.numCols() != no.numCols()) {
        if (!((yes.numCols() == 1 && no.numCols() != 1) || (yes.numCols() != 1 && no.numCols() == 1))) {
          throw new IllegalArgumentException("Column mismatch between `yes` and `no`. `yes` has " + yes.numCols() + " columns; `no` has " + no.numCols() + " columns.");
        }
      }
    } else if (yes != null) {
      if (yes.numCols() != 1)
        throw new IllegalArgumentException("Column mismatch between `yes` and `no`. `yes` has " + yes.numCols() + " columns; `no` has " + 1 + " columns.");
    } else if (no != null) {
      if (no.numCols() != 1)
        throw new IllegalArgumentException("Column mismatch between `yes` and `no`. `yes` has " + 1 + "; `no` has " + no.numCols() + ".");
    }
    Frame fr2;
    if( tst.numRows()==1 && tst.numCols()==1 ) {
      if( tst.anyVec().at(0) != 0 ) // any number other than 0 means true... R semantics
        fr2 = new Frame(new String[]{"C1"}, new Vec[]{Vec.makeCon(yes==null?dyes:yes.vecs()[0].at(0),1)});
      else
        fr2 = new Frame(new String[]{"C1"}, new Vec[]{Vec.makeCon(no==null?dno:no.vecs()[0].at(0),1)});
    } else {
      Frame a_yes = yes == null ? adaptToTst(dyes, tst) : adaptToTst(yes, tst);
      Frame a_no = no == null ? adaptToTst(dno, tst) : adaptToTst(no, tst);
      Frame frtst = (new Frame(tst)).add(a_yes).add(a_no);
      final int ycols = a_yes.numCols();

      // Run a selection picking true/false across the frame
      fr2 = new MRTask() {
        @Override
        public void map(Chunk chks[], NewChunk nchks[]) {
          int rows = chks[0]._len;
          int cols = chks.length;
          Chunk pred = chks[0];
          for (int r = 0; r < rows; ++r) {
            for (int c = (pred.atd(r) != 0 ? 1 : ycols + 1), col = 0; c < (pred.atd(r) != 0 ? ycols + 1 : cols); ++c) {
              if (chks[c].vec().isUUID())
                nchks[col++].addUUID(chks[c], r);
              else if (chks[c].vec().isString())
                nchks[col++].addStr(chks[c].atStr(new ValueString(), r));
              else
                nchks[col++].addNum(chks[c].atd(r));
            }
          }
        }
      }.doAll(yes == null ? 1 : yes.numCols(), frtst).outputFrame(yes == null ? (new String[]{"C1"}) : yes.names(), null/*same as R: no domains*/);
    }
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

    // breaks first
    AST breaks = E.parse();
    if( breaks instanceof ASTDoubleList ) _cuts = ((ASTDoubleList)breaks)._d;
    else if( breaks instanceof ASTLongList ) {
      int i=0;
      _cuts = new double[((ASTLongList)breaks)._l.length];
      for(long l: ((ASTLongList)breaks)._l) _cuts[i++]=l;
    }
    else if( breaks instanceof ASTNum )   _cuts = new double[]{((ASTNum)breaks)._d};
    else throw new IllegalArgumentException("`breaks` argument expected to be a dlist or number. Got: " + breaks.getClass());

    // labels second
    AST labels = E.parse();
    if( labels instanceof ASTStringList ) _labels = ((ASTStringList)labels)._s;
    else if( labels instanceof ASTString) _labels = new String[]{((ASTString)labels)._s};
    else if( labels instanceof ASTFrame ) _labels = new String[]{((ASTFrame)labels)._key};
    else if( labels instanceof ASTNull  ) _labels = null;
    else throw new IllegalArgumentException("`labels` argument expected to be a slist or String. Got: " + labels.getClass());

    // cleanup _labels
    if( _labels!=null )
      for (int i = 0; i < _labels.length; ++i) _labels[i] = _labels[i].replace("\"", "").replace("\'", "");

    //include.lowest
    AST inc_lowest = E.parse();
    inc_lowest = E._env.lookup((ASTId)inc_lowest);
    try {
      _includelowest = ((ASTNum) inc_lowest).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `include.lowest` expected to be TRUE/FALSE.");
    }

    //right
    AST right = E.parse();
    right = E._env.lookup((ASTId)right);
    try {
      _right = ((ASTNum) right).dbl() == 1;
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `right` expected to be a TRUE/FALSE.");
    }

    // dig.lab
    ASTNum diglab;
    try {
      diglab = (ASTNum) E.parse();
    } catch (ClassCastException e) {
      e.printStackTrace();
      throw new IllegalArgumentException("Argument `dig.lab` expected to be a number.");
    }
    _diglab = diglab.dbl();
    _diglab = _diglab >= 12 ? 12 : _diglab; // cap at 12 digits
    E.eatEnd(); // eat the ending ')'
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
                              || (!_right && x >= cuts[cuts.length-1])) nc.addNum(Double.NaN); //slightly faster than nc.addNA();
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

class ASTAsNumeric extends ASTUniPrefixOp {
  ASTAsNumeric() { super(new String[]{"as.numeric", "ary"}); }
  @Override String opStr() { return "as.numeric"; }
  @Override ASTOp make() {return new ASTAsNumeric(); }
  @Override void apply(Env env) {
    Frame ary = env.peekAry();
    Vec[] nvecs = new Vec[ary.numCols()];
    Vec vv;
    for (int c = 0; c < ary.numCols(); ++c) {
      vv = ary.vecs()[c];
      nvecs[c] = ( vv.isInt() || vv.isEnum() ) ? vv.toInt() : copyOver(vv.domain(),vv);
    }
    Frame v = new Frame(ary._names, nvecs);
    env.poppush(1, new ValFrame(v));
  }

  static private Vec copyOver(final String[] domain, final Vec vv) {
    String[][] dom = new String[1][];
    dom[0]=domain;
    final byte _type = vv.get_type();
    return new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        ValueString vstr = new ValueString();
        for(int i=0;i<c._len;++i) {
          switch( _type ) {
            case Vec.T_BAD : break; /* NOP */
            case Vec.T_STR : nc.addStr(c.atStr(vstr, i)); break;
            case Vec.T_UUID: nc.addUUID(c, i); break;
            case Vec.T_NUM : /* fallthrough */
            case Vec.T_ENUM:
            case Vec.T_TIME: nc.addNum(c.atd(i)); break;
            default:
              if (_type > Vec.T_TIME && _type <= Vec.T_TIMELAST)
                nc.addNum(c.atd(i));
              else
                throw new IllegalArgumentException("Unsupported vector type: " + _type);
              break;
          }
        }
      }
    }.doAll(1,vv).outputFrame(null,dom).anyVec();
  }
}

class ASTFactor extends ASTUniPrefixOp {
  ASTFactor() { super(new String[]{"", "ary"});}
  @Override String opStr() { return "as.factor"; }
  @Override ASTOp make() {return new ASTFactor();}
  @Override void apply(Env env) {
    Frame ary = env.popAry();
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("factor requires a single column");
    Vec v0 = ary.anyVec();
    if( v0.isEnum() ) {
      env.pushAry(ary);
      return;
    }
    Vec v1 = v0.toEnum();
    Frame fr = new Frame(ary._names, new Vec[]{v1});
    env.pushAry(fr);
  }
}

class ASTCharacter extends ASTUniPrefixOp {
  ASTCharacter() { super(new String[]{"", "ary"});}
  @Override String opStr() { return "as.character"; }
  @Override ASTOp make() {return new ASTFactor();}
  @Override void apply(Env env) {
    Frame ary = env.popAry();
    if( ary.numCols() != 1 ) throw new IllegalArgumentException("character requires a single column");
    Vec v0 = ary.anyVec();
    Vec v1 = v0.isString() ? null : v0.toStringVec(); // toEnum() creates a new vec --> must be cleaned up!
    Frame fr = new Frame(ary._names, new Vec[]{v1 == null ? v0.makeCopy(null) : v1});
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
  ASTLs parse_impl(Exec E) {
    E.eatEnd();
    return (ASTLs) clone();
  }
  @Override void apply(Env env) {
    ArrayList<String> domain = new ArrayList<>();
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
    AppendableVec av2= new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
    NewChunk keys = new NewChunk(av,0);
    NewChunk szs  = new NewChunk(av2,0);
    int r = 0;
    for( Key key : KeySnapshot.globalSnapshot().keys()) {
      keys.addEnum(r++);
      szs.addNum(getSize(key));
      domain.add(key.toString());
    }
    keys.close(fs);
    szs.close(fs);
    Vec c0 = av.close(fs);   // c0 is the row index vec
    Vec c1 = av2.close(fs);
    fs.blockForPending();
    String[] key_domain = new String[domain.size()];
    for (int i = 0; i < key_domain.length; ++i) key_domain[i] = domain.get(i);
    c0.setDomain(key_domain);
    env.pushAry(new Frame(Key.make("h2o_ls"), new String[]{"key", "byteSize"}, new Vec[]{c0,c1}));
  }

  private double getSize(Key k) {
    try { return (double) (((Frame) k.get()).byteSize()); }
    catch (Exception e) { return Double.NaN; }
  }
}

class ASTStoreSize extends ASTOp {
  ASTStoreSize() { super(null); }
  @Override String opStr() { return "store_size"; }
  @Override ASTOp make() { return new ASTStoreSize(); }
  ASTStoreSize parse_impl(Exec E) {
    E.eatEnd();
    return (ASTStoreSize) clone();
  }
  @Override void apply(Env e) { e.push(new ValNum(H2O.store_size())); }
}

// used for testing... takes in an expected number of keys, and then determines leak
// pushes TRUE for leak, FALSE for not leak
class ASTKeysLeaked extends ASTUniPrefixOp {
  ASTKeysLeaked() { super(new String[]{"numkeys"}); }
  @Override String opStr() { return "keys_leaked"; }
  @Override ASTOp make() { return new ASTKeysLeaked(); }
  ASTKeysLeaked parse_impl(Exec E) {
    AST a = E.parse();
    E.eatEnd();
    ASTKeysLeaked res = (ASTKeysLeaked) clone();
    res._asts = new AST[]{a};
    return res;
  }
  @Override void apply(Env e) {
    int numKeys = (int)e.popDbl();
    int leaked_keys = H2O.store_size() - numKeys;
    if( leaked_keys > 0 ) {
      int cnt=0;
      for( Key k : H2O.localKeySet() ) {
        Value value = H2O.raw_get(k);
        // Ok to leak VectorGroups and the Jobs list
        if( !(value.isFrame() || value.isVec() || value.get() instanceof Chunk) )
          leaked_keys--;
        else {
          if( cnt++ < 10 )
            System.err.println("Leaked key: " + k + " = " + TypeMap.className(value.type()));
        }
      }
      if( 10 < leaked_keys ) System.err.println("... and "+(leaked_keys-10)+" more leaked keys");
    }
    e.push(new ValStr(leaked_keys <= 0 ? "FALSE" : "TRUE"));
  }
}

class ASTGetTimeZone extends ASTOp {
  ASTGetTimeZone() { super(null); }
  @Override String opStr() { return "getTimeZone"; }
  @Override ASTOp make() { return new ASTGetTimeZone(); }
  ASTGetTimeZone parse_impl(Exec E) {
    E.eatEnd();
    return (ASTGetTimeZone) clone();
  }
  @Override void apply(Env env) {
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
    NewChunk tz = new NewChunk(av,0);
    String domain[] = new String[]{ParseTime.getTimezone().toString()};
    tz.addEnum(0);
    tz.close(fs);
    Vec v = av.close(fs);
    v.setDomain(domain);
    env.pushAry(new Frame(null, new String[]{"TimeZone"}, new Vec[]{v}));
  }
}

class ASTListTimeZones extends ASTOp {
  ASTListTimeZones() { super(null); }
  @Override String opStr() { return "listTimeZones"; }
  @Override ASTOp make() { return new ASTListTimeZones(); }
  ASTListTimeZones parse_impl(Exec E) {
    E.eatEnd();
    return (ASTListTimeZones) clone();
  }
  @Override void apply(Env e) {
    Futures fs = new Futures();
    AppendableVec av = new AppendableVec(Vec.VectorGroup.VG_LEN1.addVec());
    NewChunk tz = new NewChunk(av,0);
    String[] domain = ParseTime.listTimezones().split("\n");
    for(int i=0;i<domain.length;++i)
      tz.addEnum(i);
    tz.close(fs);
    Vec v = av.close(fs);
    v.setDomain(domain);
    e.pushAry(new Frame(null, new String[]{"ListTimeZones"}, new Vec[]{v}));
  }
}

class ASTSetTimeZone extends ASTOp {
  String _tz;
  ASTSetTimeZone() { super(new String[]{"tz"}); }
  @Override String opStr() { return "setTimeZone"; }
  @Override ASTOp make() { return new ASTSetTimeZone(); }
  ASTSetTimeZone parse_impl(Exec E) {
    _tz = E.nextStr();
    E.eatEnd();
    return (ASTSetTimeZone)clone();
  }
  @Override void apply(Env e) {
    Set<String> idSet = DateTimeZone.getAvailableIDs();
    if(!idSet.contains(_tz))
      throw new IllegalArgumentException("Unacceptable timezone name given.  For a list of acceptable names, use listTimezone().");
    new MRTask() {
      @Override public void setupLocal() { ParseTime.setTimezone(_tz); }
    }.doAllNodes();
  }
}

// Variable length; flatten all the component arys
class ASTCat extends ASTUniPrefixOp {
  // to keep the ordering passed to this (c ...) in the resulting Vec, maintain a list of "switches" between doubles and spans.
  // that is, keep track of how many doubles 'til the next span... Just adds a slight nuance to the already complex code below
  long[] _tilNext;
  @Override String opStr() { return "c"; }
  public ASTCat( ) { super(new String[]{"cat","dbls", "..."});}
  @Override ASTOp make() {return new ASTCat();}
  @Override ASTCat parse_impl(Exec E) {
    ArrayList<Double> dbls = new ArrayList<>();
    ArrayList<ASTSpan> spans = new ArrayList<>();
    ArrayList<Long> cnts = new ArrayList<>();
    boolean strs=false;
    long cnt=0;
    AST a;
    while( !E.isEnd() ) {
      a = E.parse();
      if( a instanceof ASTStringList ) strs = true;
      else if( a instanceof ASTDoubleList ) { cnt += addAll(dbls,((ASTDoubleList)a)._d); }
      else if( a instanceof ASTNum   ) { dbls.add(((ASTNum)a)._d); cnt++; }
      else if( a instanceof ASTSpan  ) { spans.add((ASTSpan)a); cnts.add(cnt); cnt=0; cnts.add(cnt); }
      else throw new IllegalArgumentException("'c' expected a dlist, a number, or a span. Got: " + a.getClass());
    }
    if( strs ) {
      // buildin an Enum Vec

    } else {
      cnts.add(cnt);
      _tilNext = new long[cnts.size()];
      int i = 0;
      for (long l : cnts) _tilNext[i++] = l;
      ASTSeries s = new ASTSeries(null, toArray(dbls), spans.toArray(new ASTSpan[spans.size()]));
      E.eatEnd(); // eat ending ')'
      ASTCat res = (ASTCat) clone();
      res._asts = new AST[]{s};
      return res;
    }
    return new ASTCat();
  }

  @Override void apply(Env env) {
    final ValSeries s = (ValSeries) env.pop();
    assert s._d!=null;
    long len = s._d.length;
    if (s._spans != null)
      for (ASTSpan as : s._spans) len += as.length();

    Vec v = Vec.makeZero(len);
    long[] v_espcs = v._espc;
    int nChunks = v.nChunks();

    int spanOrDbl = 0;   // index into the _tilNext array
    int dblIdx    = 0;   // index into the s._d array
    int spIdx     = 0;   // index into the s._span array
    ASTSpan sp=null;     // split span over chunks
    long splitPoint=0;   // the split point in sp if sp!=null

    // every chunk gets a Marker[], keep a map from chunk idx to Marker[]
    final Marker[][] chunkMarkers = new Marker[nChunks][];

    // build the Marker[] per chk, WARNING: delicate (read: complex) code below...
    for( int i=0;i<nChunks;++i ) {
      ArrayList<Marker> markers = new ArrayList<>();
      long clen = v_espcs[i+1] - v_espcs[i];
      Marker m=null;
      while( clen>0 ) { // chip away at clen

        // if no split span, then read doubles or span next?
        if( sp==null && spanOrDbl <_tilNext.length && _tilNext[spanOrDbl]!=0) { // 0 means read span
          long ndbls = _tilNext[spanOrDbl];

          // cases:
          //  1. more dbls to read than rows in chunk left to fill
          //  2. fewer dbls to read
          //  3. exactly clen's worth of dbls -- loop break out
          if( ndbls > clen ) {
            m = new Marker((byte)0,dblIdx,dblIdx,dblIdx+clen);
            _tilNext[spanOrDbl]-=clen;
            dblIdx += clen;
            clen=0;
          } else if( ndbls <= clen ) {
            m = new Marker((byte)0,dblIdx,dblIdx,dblIdx+ndbls);
            _tilNext[spanOrDbl++]=0;  // advance spanOrDbl when it drops to 0
            dblIdx += ndbls;
            clen-=ndbls;
          }
        } else if( s._spans!=null ) {
          // read a 0 in _tilNext or currently splitting a span
          if( sp == null ) {  // not splitting
            sp = s._spans[spIdx];

            long spLength = sp.length();
            // can read the whole span into the chunk
            if( sp.length() <= clen ) {
              m = new Marker((byte)1,spIdx,sp._min,(long)sp._max);
              sp=null;
              spIdx++;
              spanOrDbl++;
              clen-=spLength;

            // must split!
            } else {
              splitPoint = sp._min+clen-1;
              m = new Marker((byte)1,spIdx,sp._min,splitPoint);
              clen=0;
            }

          // got a split span
          } else {
            long leftInSpan = (long)sp._max - splitPoint + 1;

            // can we fit the rest of the span into this chunk
            if( leftInSpan <= clen ) {
              m = new Marker((byte)1,spIdx,splitPoint+1,(long)sp._max);
              // advance pointers, null out split span
              sp = null;
              splitPoint=0;
              spIdx++; // done with the span
              spanOrDbl++;
              clen= clen - leftInSpan + 1;

            // split the span again
            } else if( leftInSpan > clen) {
              m = new Marker((byte)1,spIdx,splitPoint+1,splitPoint+1+clen);
              splitPoint += clen;
              clen=0;
            }
          }
        }
        markers.add(m);
      }
      chunkMarkers[i] = markers.toArray(new Marker[markers.size()]);
    }

    Frame fr = new MRTask() {
      @Override public void map(Chunk c) {
        int cidx=c.cidx();
        int i=0; // idx into Chunk
        Marker[] markers = chunkMarkers[cidx];

        for( Marker m:markers )
          if( m._t == 0 )
            for(long j=m._start; j<m._stop; ++j)
              c.set(i++,s._d[(int)j]);
          else
            for(long j=m._start;j<=m._stop;++j)
              c.set(i++,j);
      }
    }.doAll(v)._fr;
    env.pushAry(fr);
  }

  private static long addAll(ArrayList<Double> dbls, double[] d){ for(double dd:d) dbls.add(dd); return d.length; } // return number of doubles added.
  private static double[] toArray(ArrayList<Double> dbls) {
    double[] r = new double[dbls.size()];
    int i = 0;
    for( double d:dbls ) r[i++] = d;
    return r;
  }

  private class Marker extends Iced {
    final byte _t;     // either 0 or 1; 0: double; 1: span
    final int _idx;    // index into the appropriate array (based on _t)
    final long _start; // where to start; for _t=0, _start == _idx
    final long _stop;  // where to stop
    Marker(byte t, int idx, long start, long stop) { _t=t; _idx=idx; _start=start; _stop=stop; }
  }
}

class ASTWhich extends ASTUniPrefixOp {  // 1-based index
  ASTWhich() {super(null); }
  @Override String opStr() { return "h2o.which"; }
  @Override ASTWhich make() { return new ASTWhich(); }
  @Override ASTWhich parse_impl(Exec E) {
    AST condition = E.parse();
    ASTWhich res = (ASTWhich)clone();
    res._asts = new AST[]{condition};
    return res;
  }
  @Override public void apply(Env e) {
    Frame f=e.popAry();
    if( f.numRows()==1 && f.numCols() > 1) {
      double[] in = new double[f.numCols()];
      for(int i=0;i<in.length;++i) in[i] = f.vecs()[i].at(0);
      double[] out = map(null,in,null,null);
      Futures fs = new Futures();
      Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
      AppendableVec v = new AppendableVec(key);
      NewChunk chunk = new NewChunk(v, 0);
      for (double d : out) chunk.addNum(d);
      chunk.close(0, fs);
      Vec vec = v.close(fs);
      fs.blockForPending();
      Frame fr2 = new Frame(vec);
      e.pushAry(fr2);
      return;
    }
    Frame f2 = new MRTask() {
      @Override public void map(Chunk c, NewChunk nc) {
        long start = c.start();
        for(int i=0;i<c._len;++i)
          if( c.at8(i)==1 ) nc.addNum(1+start+i);
      }
    }.doAll(1,f.anyVec()).outputFrame();
    e.pushAry(f2);
  }
  @Override double[] map(Env env, double[] in, double[] out, AST[] args) {
    ArrayList<Integer> w = new ArrayList<>();
    for(int i=0; i < in.length;++i)
      if( in[i]==1 ) w.add(i+1);
    out=new double[w.size()];
    for(int i=0;i<w.size();++i) out[i]=w.get(i);
    return out;
  }
}

class ASTWhichMax extends ASTUniPrefixOp {  // 1-based index
  ASTWhichMax() {super(null); }
  @Override ASTWhichMax make() { return new ASTWhichMax(); }
  @Override ASTWhichMax parse_impl(Exec E) {
    AST condition = E.parse();
    ASTWhichMax res = (ASTWhichMax)clone();
    res._asts = new AST[]{condition};
    return res;
  }
  @Override String opStr() { return "h2o.which.max"; }
  @Override public void apply(Env e) {
    Frame f=e.popAry();
    if( f.numRows()==1 && f.numCols() > 1) {
      int idx=0;
      double max = -Double.MAX_VALUE;
      for(int i=0;i<f.numCols();++i) {
        double val=f.vecs()[i].at(0);
        if( val > max ) {
          max = val;
          idx = i;
        }
      }
      Futures fs = new Futures();
      Key key = Vec.VectorGroup.VG_LEN1.addVecs(1)[0];
      AppendableVec v = new AppendableVec(key);
      NewChunk chunk = new NewChunk(v, 0);
      chunk.addNum(idx+1,0);
      chunk.close(0, fs);
      Vec vec = v.close(fs);
      fs.blockForPending();
      Frame fr2 = new Frame(vec);
      e.pushAry(fr2);
      return;
    }
    Frame f2 = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk nc) {
        for(int row=0;row<c[0]._len;++row) {
          double max=-Double.MAX_VALUE;
          int idx=0;
          for(int col=0;col<c.length;++col) {
            double val = c[col].atd(row);
            if( val > max ) {
              max = val;
              idx = col;
            }
          }
          nc.addNum(idx+1);
        }
      }
    }.doAll(1,f).outputFrame();
    e.pushAry(f2);
  }
}

class ASTMajorityVote extends ASTUniPrefixOp {  // 1-based index
  int _n;
  double[] _weights;
  ASTMajorityVote() {super(null); }
  @Override String opStr() { return "h2o.vote"; }
  @Override ASTMajorityVote make() { return new ASTMajorityVote(); }
  @Override ASTMajorityVote parse_impl(Exec E) {
    AST condition = E.parse();
    _n = (int)E.nextDbl();  // number of classes
    AST a = E.parse();
    if( a instanceof ASTNum ) _weights = new double[]{((ASTNum)a)._d};
    else if( a instanceof ASTLongList ) {
      long[] l = ((ASTLongList)a)._l;
      _weights = new double[l.length];
      for(int i =0;i<l.length;++i) _weights[i] = l[i];
    } else {
      _weights = ((ASTDoubleList) a)._d;
    }
    ASTMajorityVote res = (ASTMajorityVote)clone();
    res._asts = new AST[]{condition};
    return res;
  }
  @Override public void apply(Env e) {
    Frame f=e.popAry();
    final int n=_n;
    final double[] weights = _weights;
    Frame f2 = new MRTask() {
      @Override public void map(Chunk[] c, NewChunk nc) {
        double[] votes = new double[n+1];
        for(int row=0;row<c[0]._len;++row) {
          for(int i=0;i<votes.length;++i)votes[i]=0; // rezero the array each time
          for( int col=0;col<c.length;++col) { votes[(int)c[col].at8(row)] += weights[col]; };// weights[(int)(4*col+(c[col].at8(row)-1))]; }
          int i=0;
          double max=votes[i];
          int iter=0;
          while(iter < votes.length) {
            if (votes[iter] > max) { max = votes[i]; i = iter; }
            iter++;
          }
          nc.addNum(i); // 1-based index
        }
      }
    }.doAll(1,f).outputFrame();
    e.pushAry(f2);
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
    AST r = E.parse();
    E.eatEnd(); // eat the ending ')'
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
    ASTTranspose res = new ASTTranspose();
    E.eatEnd(); // eat the ending ')'
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
//  protected boolean _rclosed;
//  protected double _x;
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
