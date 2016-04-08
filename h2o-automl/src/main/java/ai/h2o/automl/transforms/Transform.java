package ai.h2o.automl.transforms;

import org.reflections.Reflections;
import water.fvec.Vec;
import water.rapids.ASTUniOp;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stupid class just to get the proverbial ball rolling.
 * Eventually, will want to javassist (i.e. fuse) transforms over multiple vecs
 * (rather than 1 at a time, which is the current limitation of this class).
 *
 * A pile of heuristics (rules) and models drive the choice of transforms by the column to
 * be transformed. The set of transforms (in the String[] ops) are the names of the AST
 * operations
 *
 * Here are the features that are currently going to be generated:
 *      1. apply unary op to a single column
 *      2. multiply/add/subtract/divide two columns
 *      3. interact two columns (R style interaction)
 */
public class Transform {
  public static final String[] uniOps;
  static {
    List<String> ops = new ArrayList<>();

    Reflections reflections = new Reflections("water.rapids");

    Set<Class<? extends ASTUniOp>> uniOpClasses =
            reflections.getSubTypesOf(water.rapids.ASTUniOp.class);

    for(Class c: uniOpClasses)
      ops.add(c.getSimpleName().substring("AST".length()));

    ops.add("Ignore"); ops.add("Impute"); ops.add("Cut");  // Ignore means do not include.. no transformation necessary!

    uniOps = ops.toArray(new String[ops.size()]);
  }
  public static void printOps() { for(String op: uniOps) System.out.println(op); }


  // for AutoCollect type scenario:
  //  1. decide if situation 1, 2, or 3
  //  2. "apply" the transformation (push the transformed vec onto the pile)
}


/**
 * Build Rapids expressions with ye olde java objects.
 *
 * Expr class used by AutoML manufactures Rapids ASTFun-like expressions
 * in particular, since those are the kind that are required by the
 * TransformWrappedVec class. This is all in effort to be extremely
 * lean in experimenting with transformations on columns of data. We
 * can try new experiments as fast as we can construct DAGs of Exprs without
 * worrying about memory overhead. There is going to be some (possibly significant)
 * compute overhead per-row, but the nature of this overhead is still yet
 * undetermined and requires further analysis.
 *
 * A quick note on the _argCnter:
 *   Since Expr builds ASTFun-like Rapids expressions, each unique Vec in the expression
 *   must be represented by an input argument. Recall that ASTFun exprs look like this:
 *         { x1 x2 . (bod) }
 *   where x1 and x2 are two args that appear in the body of the function. The arguments
 *   are specified as above (single space-separated Strings that begin with a letter).
 *
 *   What we're doing with the _argCnter is simply assigning an argument number to each
 *   unique Vec that enters into the expression, so that when the toRapids call is invoked
 *   the Vec can produce the appropriate argument name.
 *
 *   While we could just use the Vec's existing _key field, we choose the _argCnter for
 *   improved debugging.
 *
 * API Goals:
 *    Expr.plus(v1,v2);  add two vecs
 *    Expr.plus(v,5);    add 5 to a vec
 *    e.plus(e,v);       add a vec to an existing Expr
 *    e.log()            take a log of the entire expression
 *    Expr.log(v)        take the log a vec
 *
 *    Is this legal?
 *       Expr.plus(v1, Expr.log(v2));
 *       e.plus(e, Expr.log(v))
 *
 *
 *
 */
class Expr {
  ArrayList<Expr> _exprs; // dag
  String _op;             // opcode string ("+", "log", etc.)
  static final String ARGPREFIX="x"; // the arg prefix  { x1 x2 x3 .

  protected Expr() {}
  private Expr(String op, Expr... exprs) {
    Expr e = new Expr();
    e._op=op;
    e._exprs = new ArrayList<>();
    Collections.addAll(e._exprs, exprs);
  }

  public static Expr binOp(String op, Expr l, Expr r)   { return new Expr(op,l,r); }
  public static Expr binOp(String op, Expr l, Vec r)    { return new Expr(op,l, new ExprVec(r)); }
  public static Expr binOp(String op, Expr l, double r) { return new Expr(op,l, new ExprNum(r)); }
  public static Expr binOp(String op, Vec l, Expr r)    { return new Expr(op, new ExprVec(l),r); }
  public static Expr binOp(String op, Vec l, Vec r)     { return new Expr(op, new ExprVec(l), new ExprVec(r)); }
  public static Expr binOp(String op, Vec l, double r)  { return new Expr(op, new ExprVec(l),new ExprNum(r)); }
  public static Expr binOp(String op, double l, Expr r) { return new Expr(op, new ExprNum(l),r); }
  public static Expr binOp(String op, double l, Vec r)  { return new Expr(op, new ExprNum(l), new ExprVec(r)); }
  // op(double, double) is not interesting...

  /**
   * Construct a Rapids expression!
   * @return String that is a rapids expression
   */
  public String toRapids() {
    AtomicInteger argCnter = new AtomicInteger(1);
    assignArgs(argCnter);
    return asRapids().toString();
  }

  StringBuilder asRapids() {
    StringBuilder sb = new StringBuilder("(");
    sb.append(_op);
    for(Expr expr: _exprs)
      sb.append(" ").append(expr.toRapids());
    return sb;
  }

  void assignArgs(AtomicInteger argCnter) {
    for(Expr expr: _exprs)
      expr.assignArgs(argCnter);
  }
}

class ExprNum extends Expr {
  double _d;
  ExprNum(double d) { _d=d; }
  @Override StringBuilder asRapids() { return new StringBuilder(""+_d); }
  @Override void assignArgs(AtomicInteger argCnter) { }
}

class ExprVec extends Expr {
  Vec _v;
  String _argName;
  ExprVec(Vec v) { _v=v; }
  @Override StringBuilder asRapids() { return new StringBuilder(_argName); }
  @Override void assignArgs(AtomicInteger argCnter) { _argName = ARGPREFIX+argCnter.getAndIncrement(); }

}