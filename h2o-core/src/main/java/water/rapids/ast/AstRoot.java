package water.rapids.ast;

import water.Iced;
import water.fvec.Frame;
import water.rapids.*;
import water.rapids.ast.params.AstId;
import water.rapids.ast.params.AstConst;
import water.rapids.ast.params.AstStr;
import water.rapids.ast.prims.advmath.*;
import water.rapids.ast.prims.assign.*;
import water.rapids.ast.prims.matrix.AstMMult;
import water.rapids.ast.prims.matrix.AstTranspose;
import water.rapids.ast.prims.misc.AstComma;
import water.rapids.ast.prims.misc.AstLs;
import water.rapids.ast.prims.mungers.*;
import water.rapids.ast.prims.operators.*;
import water.rapids.ast.prims.reducers.*;
import water.rapids.ast.prims.repeaters.AstRepLen;
import water.rapids.ast.prims.repeaters.AstSeq;
import water.rapids.ast.prims.repeaters.AstSeqLen;
import water.rapids.ast.prims.search.AstMatch;
import water.rapids.ast.prims.search.AstWhich;
import water.rapids.ast.prims.string.*;
import water.rapids.ast.prims.time.*;
import water.rapids.ast.prims.math.*;
import water.rapids.ast.prims.timeseries.*;

import java.util.HashMap;

/**
 * Rapids expression Abstract Syntax Tree.
 * <p/>
 * Subclasses define the program semantics
 */
public abstract class AstRoot extends Iced<AstRoot> {
  // Subclasses define their execution.  Constants like Numbers & Strings just
  // return a ValXXX.  Constant functions also just return a ValFun.

  // AstExec is Function application, and evaluates the 1st arg and calls
  // 'apply' to evaluate the remaining arguments.  Usually 'apply' is just
  // "exec all args" then apply a primitive function op to the args, but for
  // logical AND/OR and IF statements, one or more arguments may never be
  // evaluated (short-circuit evaluation).
  public abstract Val exec(Env env);

  // Default action after the initial execution of a function.  Typically the
  // action is "execute all arguments, then apply a primitive action to the
  // arguments", but short-circuit evaluation may not execute all args.
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    throw new UnsupportedOperationException();
  }

  // Short name (there's lots of the simple math primtives, and we want them to
  // fit on one line)
  public abstract String str();

  @Override
  public String toString() {
    return str();
  }

  public abstract String example();

  public abstract String description();

  // Number of arguments, if that makes sense.  Always count 1 for self, so a
  // binary operator like '+' actually has 3 nargs: ["+", lhs, rhs].
  // For variable-argument expressions this method should return -1.
  public abstract int nargs();

  // Select columns by number or String.
  // TODO: clarify meaning
  public int[] columns(String[] names) {
    throw new IllegalArgumentException("Requires a number-list, but found an " + getClass().getSimpleName());
  }

  // Built-in constants, checked before other namespace lookups happen
  public static final HashMap<String, AstPrimitive> PRIMS = new HashMap<>();
  // Built-in primitives, done after other namespace lookups happen
  public static final HashMap<String, AstParameter> CONSTS = new HashMap<>();

  static void init(AstPrimitive ast) {
    PRIMS.put(ast.str(), ast);
  }

  static {
    // Constants
    CONSTS.put("FALSE", AstConst.FALSE);
    CONSTS.put("False", AstConst.FALSE);
    CONSTS.put("false", AstConst.FALSE);
    CONSTS.put("TRUE", AstConst.TRUE);
    CONSTS.put("True", AstConst.TRUE);
    CONSTS.put("true", AstConst.TRUE);
    CONSTS.put("NaN", AstConst.NAN);
    CONSTS.put("NA", AstConst.NAN);
    CONSTS.put("PI", AstConst.PI);
    CONSTS.put("Pi", AstConst.PI);
    // fails to compile on some systems due to JavaDoc failure: CONSTS.put("<pi character>", AstConst.PI);

    // Standard math functions
    init(new AstAbs());
    init(new AstAcos());
    init(new AstAcosh());
    init(new AstAsin());
    init(new AstAsinh());
    init(new AstAtan());
    init(new AstAtanh());
    init(new AstCeiling());
    init(new AstCos());
    init(new AstCosh());
    init(new AstCosPi());
    init(new AstDiGamma());
    init(new AstExp());
    init(new AstExpm1());
    init(new AstFloor());
    init(new AstGamma());
    init(new AstLGamma());
    init(new AstLog());
    init(new AstLog1P());
    init(new AstLog2());
    init(new AstLog10());
    init(new AstNoOp());
    init(new AstNot());
    init(new AstRound());
    init(new AstSgn());
    init(new AstSignif());
    init(new AstSin());
    init(new AstSinh());
    init(new AstSinPi());
    init(new AstSqrt());
    init(new AstTan());
    init(new AstTanh());
    init(new AstTanPi());
    init(new AstTriGamma());
    init(new AstTrunc());

    // Math binary operators
    init(new AstAnd());
    init(new AstDiv());
    init(new AstEq());
    init(new AstGe());
    init(new AstGt());
    init(new AstIntDiv());
    init(new AstIntDivR());
    init(new AstLAnd());
    init(new AstLe());
    init(new AstLOr());
    init(new AstLt());
    init(new AstMod());
    init(new AstModR());
    init(new AstMul());
    init(new AstNe());
    init(new AstOr());
    init(new AstPlus());
    init(new AstPow());
    init(new AstSub());
    init(new AstIfElse());
    init(new AstIfElse()); // this one is ternary

    // Reducers
    init(new AstAll());
    init(new AstAny());
    init(new AstAnyNa());
    init(new AstCumMax());
    init(new AstCumMin());
    init(new AstCumProd());
    init(new AstCumSum());
    init(new AstMad());
    init(new AstMax());
    init(new AstMaxNa());
    init(new AstMean());
    init(new AstMedian());
    init(new AstMin());
    init(new AstMinNa());
    init(new AstNaCnt());
    init(new AstProd());
    init(new AstProdNa());
    init(new AstSdev());
    init(new AstSum());
    init(new AstSumNa());

    // Time
    init(new AstAsDate());
    init(new AstDay());
    init(new AstDayOfWeek());
    init(new AstGetTimeZone());
    init(new AstHour());
    init(new AstListTimeZones());
    init(new AstMillis());
    init(new AstMinute());
    init(new AstMktime());
    init(new AstMonth());
    init(new AstSecond());
    init(new AstSetTimeZone());
    init(new AstWeek());
    init(new AstYear());

    //Time Series
    init(new AstDiffLag1());

    // Advanced Math
    init(new AstCorrelation());
    init(new AstHist());
    init(new AstImpute());
    init(new AstKFold());
    init(new AstMode());
    init(new AstSkewness());
    init(new AstKurtosis());
    init(new AstModuloKFold());
    init(new AstQtile());
    init(new AstRunif());
    init(new AstSort());
    init(new AstStratifiedKFold());
    init(new AstStratifiedSplit());
    init(new AstTable());
    init(new AstUnique());
    init(new AstVariance());

    // Generic data mungers
    init(new AstAnyFactor());
    init(new AstApply());
    init(new AstAsFactor());
    init(new AstAsCharacter());
    init(new AstAsNumeric());
    init(new AstCBind());
    init(new AstColNames());
    init(new AstColPySlice());
    init(new AstColSlice());
    init(new AstCut());
    init(new AstDdply());
    init(new AstFilterNaCols());
    init(new AstFlatten());
    init(new AstGroup());
    init(new AstGroupedPermute());
    init(new AstIsCharacter());
    init(new AstIsFactor());
    init(new AstIsNa());
    init(new AstIsNumeric());
    init(new AstLevels());
    init(new AstMerge());
    init(new AstNaOmit());
    init(new AstColumnsByType());
    init(new AstNcol());
    init(new AstNLevels());
    init(new AstNrow());
    init(new AstRBind());
    init(new AstReLevel());
    init(new AstRename());
    init(new AstRowSlice());
    init(new AstScale());
    init(new AstSetDomain());
    init(new AstSetLevel());

    // Assignment; all of these lean heavily on Copy-On-Write optimizations.
    init(new AstAppend());      // Add a column
    init(new AstAssign());      // Overwrite a global
    init(new AstRectangleAssign()); // Overwrite a rectangular slice
    init(new AstRm());          // Remove a frame, but maintain internal sharing
    init(new AstTmpAssign());   // Create a new immutable tmp frame

    // Matrix functions
    init(new AstTranspose());
    init(new AstMMult());

    // String functions
    init(new AstCountMatches());
    init(new AstCountSubstringsWords());
    init(new AstEntropy());
    init(new AstLStrip());
    init(new AstReplaceAll());
    init(new AstReplaceFirst());
    init(new AstRStrip());
    init(new AstStrLength());
    init(new AstStrSplit());
    init(new AstSubstring());
    init(new AstToLower());
    init(new AstToUpper());
    init(new AstTrim());

    // Miscellaneous
    init(new AstComma());
    init(new AstLs());

    // Search
    init(new AstMatch());
    init(new AstWhich());

    // Repeaters
    init(new AstRepLen());
    init(new AstSeq());
    init(new AstSeqLen());

  }

  public static AstId newAstFrame(Frame f) {
    return new AstId(f._key.toString());
  }

  public static AstStr newAstStr(String s) {
    return new AstStr(s);
  }
}
