package water.currents;

import water.Iced;
import water.fvec.Frame;

import java.util.HashMap;

/**
 * Abstract Syntax Tree
 *
 * Subclasses define the program semantics
 */
abstract public class AST extends Iced {
  // Subclasses define their execution.  Constants like Numbers & Strings just
  // return a ValXXX.  Constant functions also just return a ValFun.

  // ASTExec is Function application, and evaluates the 1st arg and calls
  // 'apply' to evaluate the remaining arguments.  Usually 'apply' is just
  // "exec all args" then apply a primitive function op to the args, but for
  // logical AND/OR and IF statements, one or more arguments may never be
  // evaluated (short-circuit evaluation).
  abstract Val exec( Env env );

  // Default action after the initial execution of a function.  Typically the
  // action is "execute all arguments, then apply a primitive action to the
  // arguments", but short-circuit evaluation may not execute all args.
  Val apply( Env env, Env.StackHelp stk, AST asts[] ) { throw water.H2O.fail(); }

  // Row-wise apply.  All arguments evaluated down to an array of primitive
  // doubles.  Only allowed to return a simple double.
  double rowApply( double ds[] ) { throw water.H2O.fail(); }

  // Short name (there's lots of the simple math primtives, and we want them to
  // fit on one line)
  abstract String str();
  @Override public String toString() { return str(); }

  // Number of arguments, if that makes sense.  Always count 1 for self, so a
  // binary operator like '+' actually has 3 nargs.
  abstract int nargs();

  // Built-in primitives, done after other namespace lookups happen
  static final HashMap<String,AST> PRIMS = new HashMap<>();
  static void init(AST ast) { PRIMS.put(ast.str(),ast); }
  static {
    // Constants
    init(new ASTNum(0) {public String str() { return "FALSE"; } } );
    init(new ASTNum(1) {public String str() { return "TRUE" ; } } );

    // Math unary ops
    init(new ASTACos  ());
    init(new ASTAbs   ());
    init(new ASTCeiling());
    init(new ASTCos   ());
    init(new ASTCosh  ());
    init(new ASTExp   ());
    init(new ASTFloor ());
    init(new ASTLog   ());
    init(new ASTRound ());
    init(new ASTRound ());
    init(new ASTSignif());
    init(new ASTSin   ());
    init(new ASTSqrt  ());
    init(new ASTTan   ());
    init(new ASTTanh  ());
    init(new ASTTrunc ());

    // Math binary ops
    init(new ASTAnd ());
    init(new ASTDiv ());
    init(new ASTMul ());
    init(new ASTOr  ());
    init(new ASTPlus());
    init(new ASTPow ());
    init(new ASTSub ());

    // Relational
    init(new ASTGE());
    init(new ASTGT());
    init(new ASTLE());
    init(new ASTLT());
    init(new ASTEQ());
    init(new ASTNE());

    // Logical - includes short-circuit evaluation
    init(new ASTLAnd());
    init(new ASTLOr());
    init(new ASTIfElse());

    // Reducers
    init(new ASTAll());
    init(new ASTMax());
    init(new ASTMaxNA());
    init(new ASTMean());
    init(new ASTMean());
    init(new ASTMin());
    init(new ASTMinNA());
    init(new ASTSdev());
    init(new ASTSum());
    init(new ASTSumNA());

    // Complex Math
    init(new ASTVariance());

    // Generic data mungers
    init(new ASTAsFactor());
    init(new ASTCBind());
    init(new ASTColNames());
    init(new ASTColSlice());
    init(new ASTRBind());
    init(new ASTRowSlice());
    init(new ASTRowSliceAssign());

    // Functional data mungers
    init(new ASTApply());
    init(new ASTComma());

    // Cluster management
    init(new ASTLs());

    // Model Calls
    init(new ASTQtile());
  }
}

/** A number.  Execution is just to return the constant. */
class ASTNum extends AST {
  final ValNum _d;
  ASTNum( Exec e ) { _d = new ValNum(Double.valueOf(e.token())); }
  ASTNum( double d ) { _d = new ValNum(d); }
  @Override public String str() { return _d.toString(); }
  @Override Val exec( Env env ) { return _d; }
  @Override int nargs() { return 1; }
}

/** A String.  Execution is just to return the constant. */
class ASTStr extends AST {
  final ValStr _str;
  ASTStr(Exec e, char c) { _str = new ValStr(e.match(c)); }
  @Override public String str() { return _str.toString(); }
  @Override Val exec(Env env) { return _str; }
  @Override int nargs() { return 1; }
}

/** A Frame.  Execution is just to return the constant. */
class ASTFrame extends AST {
  final ValFrame _fr;
  ASTFrame(Frame fr) { _fr = new ValFrame(fr); }
  @Override public String str() { return _fr.toString(); }
  @Override Val exec(Env env) { return _fr; }
  @Override int nargs() { return 1; }
}

/** An ID.  Execution does lookup in the current scope. */
class ASTId extends AST {
  final String _id;
  ASTId(Exec e) { _id = e.token(); }
  @Override public String str() { return _id; }
  @Override Val exec(Env env) { return env.lookup(_id); }
  @Override int nargs() { return 1; }
}

/** A primitive operation.  Execution just returns the function.  *Application*
 *  (not execution) applies the function to the arguments. */
abstract class ASTPrim extends AST {
  @Override Val exec( Env env ) { return new ValFun(this); }
}
