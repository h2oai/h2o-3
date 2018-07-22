package water.rapids;

import hex.Model;
import water.*;
import water.fvec.Frame;
import water.rapids.ast.*;
import water.rapids.ast.params.AstConst;
import water.rapids.ast.prims.advmath.*;
import water.rapids.ast.prims.assign.*;
import water.rapids.ast.prims.math.*;
import water.rapids.ast.prims.matrix.*;
import water.rapids.ast.prims.misc.*;
import water.rapids.ast.prims.mungers.*;
import water.rapids.ast.prims.operators.*;
import water.rapids.ast.prims.reducers.*;
import water.rapids.ast.prims.repeaters.*;
import water.rapids.ast.prims.search.*;
import water.rapids.ast.prims.string.*;
import water.rapids.ast.prims.time.*;
import water.rapids.ast.prims.timeseries.*;
import water.rapids.vals.ValFrame;
import water.rapids.vals.ValFun;
import water.rapids.vals.ValModel;

import java.io.Closeable;
import java.util.ArrayList;
import java.util.HashMap;

/**
 * Execute a set of instructions in the context of an H2O cloud.
 * <p/>
 * An Env (environment) object is a classic stack of values used during
 * execution of an AstRoot.  The stack is hidden in the normal Java execution
 * stack and is not explicit.
 * <p/>
 * For efficiency, reference counting is employed to recycle objects already
 * in use rather than creating copies upon copies (a la R).  When a Frame is
 * `pushed` on to the stack, its reference count is incremented by 1.  When a
 * Frame is `popped` off of the stack, its reference count is decremented by
 * 1.  When the reference count is 0, the Env instance will dispose of the
 * object.  All objects live and die by the Env's that create them.  That
 * means that any object not created by an Env instance shalt not be
 * DKV.removed.
 * <p/>
 * Therefore, the Env class is a stack of values + an API for reference counting.
 */
public class Env extends Iced {

  // Session holds the ref-counts across multiple executions.
  public final Session _ses;

  // Current lexical scope lookup
  public AstFunction _scope;


  // Frames that are alive in mid-execution; usually because we have evaluated
  // some first expression and need to hang onto it while evaluating the next
  // expression.
  private ArrayList<Frame> _stk = new ArrayList<>();

  // Built-in constants, checked before other namespace lookups happen
  private static final HashMap<String, AstPrimitive> PRIMS = new HashMap<>();
  // Built-in primitives, done after other namespace lookups happen
  private static final HashMap<String, AstParameter> CONSTS = new HashMap<>();

  static void init(AstPrimitive ast) {
    PRIMS.put(ast.str(), ast);
  }

  static void init(AstPrimitive ast, String name) {
    PRIMS.put(name, ast);
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
    CONSTS.put("nan", AstConst.NAN);
    CONSTS.put("PI", AstConst.PI);
    CONSTS.put("Pi", AstConst.PI);
    CONSTS.put("null", null);

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
    init(new AstNot(), "!!");
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
    init(new AstSumAxis());
    init(new AstSumNa());
    init(new AstTopN());  // top N%

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
    init(new AstMoment());
    init(new AstMonth());
    init(new AstSecond());
    init(new AstSetTimeZone());
    init(new AstWeek());
    init(new AstYear());

    // Time Series
    init(new AstDiffLag1());
    init(new AstIsax());

    // Advanced Math
    init(new AstCorrelation());
    init(new AstDistance());
    init(new AstHist());
    init(new AstFillNA());
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
    init(new AstGetrow());
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
    init(new AstPivot());
    init(new AstRankWithinGroupBy()); // provide ranking withing groupby groups sorted after certain columns
//    init(new AstTargetEncoderFit()); // we register it with services approach

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
    init(new AstGrep());
    init(new AstReplaceAll());
    init(new AstReplaceFirst());
    init(new AstRStrip());
    init(new AstStrLength());
    init(new AstStrSplit());
    init(new AstTokenize());
    init(new AstSubstring());
    init(new AstToLower());
    init(new AstToUpper());
    init(new AstTrim());
    init(new AstStrDistance());

    // Miscellaneous
    init(new AstComma());
    init(new AstLs());
    init(new AstSetProperty());

    // Search
    init(new AstMatch());
    init(new AstWhich());
    init(new AstWhichMax());
    init(new AstWhichMin());

    // Repeaters
    init(new AstRepLen());
    init(new AstSeq());
    init(new AstSeqLen());

    // Custom (eg. algo-specific)
    for (AstPrimitive prim : PrimsService.INSTANCE.getAllPrims())
      init(prim);
  }



  public Env(Session ses) {
    _ses = ses;
  }

  public int sp() {
    return _stk.size();
  }

  private Frame peek(int x) {
    return _stk.get(sp() + x);
  }

  // Deletes dead Frames & forces good stack cleanliness at opcode end.  One
  // per Opcode implementation.  Track frames that are alive mid-execution, but
  // dead at Opcode end.
  public StackHelp stk() {
    return new StackHelp();
  }

  public class StackHelp implements Closeable {
    final int _sp = sp();

    // Push & track.  Called on every Val that spans a (nested) exec call.
    // Used to track Frames with lifetimes spanning other AstRoot executions.
    public Val track(Val v) {
      if (v instanceof ValFrame) track(v.getFrame());
      return v;
    }

    public Frame track(Frame fr) {
      _stk.add(sp(), new Frame(fr._names, fr.vecs().clone())); // Push and track a defensive copy
      return fr;
    }

    // Pop-all and remove dead.  If a Frame was not "tracked" above, then if it
    // goes dead it will leak on function exit.  If a Frame is returned from a
    // function and not declared "returning", any Vecs it shares with Frames
    // that are dying in this opcode will be deleted out from under it.
    @Override
    public void close() {
      Futures fs = null;
      int sp = sp();
      while (sp > _sp) {
        Frame fr = _stk.remove(--sp); // Pop and stop tracking
        fs = _ses.downRefCnt(fr, fs);  // Refcnt -1 all Vecs, and delete if zero refs
      }
      if (fs != null) fs.blockForPending();
    }

    // Pop last element and lower refcnts - but do not delete.  Lifetime is
    // responsibility of the caller.
    public Val untrack(Val vfr) {
      if (!vfr.isFrame()) return vfr;
      Frame fr = vfr.getFrame();
      _ses.addRefCnt(fr, -1);           // Lower counts, but do not delete on zero
      return vfr;
    }

  }

  // If an opcode is returning a Frame, it must call "returning(frame)" to
  // track the returned Frame.  Otherwise shared input Vecs who's last use is
  // in this opcode will get deleted as the opcode exits - even if they are
  // shared in the returning output Frame.
  public <V extends Val> V returning(V val) {
    if (val instanceof ValFrame)
      _ses.addRefCnt(val.getFrame(), 1);
    return val;
  }

  // ----
  // Variable lookup

  public Val lookup(String id) {
    // Lexically scoped functions first
    Val val = _scope == null ? null : _scope.lookup(id);
    if (val != null) return val;

    // disallow TRUE/FALSE/NA to be overwritten by keys in the DKV... just way way saner this way
    if (CONSTS.containsKey(id)) {
      return CONSTS.get(id).exec(this);
    }

    // Now the DKV
    Value value = DKV.get(Key.make(expand(id)));
    if (value != null) {
      if (value.isFrame())
        return addGlobals((Frame) value.get());
      if (value.isModel())
        return new ValModel((Model) value.get());
      // Only understand Frames right now
      throw new IllegalArgumentException("DKV name lookup of " + id + " yielded an instance of type " + value.className() + ", but only Frame & Model are supported");
    }

    // Now the built-ins
    AstPrimitive ast = PRIMS.get(id);
    if (ast != null)
      return new ValFun(ast);

    throw new IllegalArgumentException("Name lookup of '" + id + "' failed");
  }

  public String expand(String id) {
    return id.startsWith("$")? id.substring(1) + "~" + _ses.id() : id;
  }

  // Add these Vecs to the global list, and make a new defensive copy of the
  // frame - so we can hack it without changing the global frame view.
  ValFrame addGlobals(Frame fr) {
    _ses.addGlobals(fr);
    return new ValFrame(new Frame(fr._names.clone(), fr.vecs().clone()));
  }

  /*
   * Utility & Cleanup
   */

  @Override
  public String toString() {
    String s = "{";
    for (int i = 0, sp = sp(); i < sp; i++) s += peek(-sp + i).toString() + ",";
    return s + "}";
  }

}
