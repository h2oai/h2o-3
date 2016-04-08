package ai.h2o.automl.transforms;

import org.reflections.Reflections;
import water.Key;
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
 */
class Expr implements Cloneable {
  ArrayList<Expr> _exprs; // dag
  String _op;             // opcode string ("+", "log", etc.)
  static final String ARGPREFIX="x"; // the arg prefix  { x1 x2 x3 .
  static HashMap<Key, String> _argNames = new HashMap<>(); // mapping of Vec keys to arg names
  static AtomicInteger _argCnter = new AtomicInteger(0);   // argument counter, for labeling args nicely

  protected Expr() {}
  private Expr(String op, Expr... exprs) { compose(op, exprs); }

  public static Expr binOp(String op, Expr l, Expr r)   { return new Expr(op,l,r); }
  public static Expr binOp(String op, Expr l, Vec r)    { return new Expr(op,l, new ExprVec(r)); }
  public static Expr binOp(String op, Expr l, double r) { return new Expr(op,l, new ExprNum(r)); }
  public static Expr binOp(String op, Vec l, Expr r)    { return new Expr(op, new ExprVec(l),r); }
  public static Expr binOp(String op, Vec l, Vec r)     { return new Expr(op, new ExprVec(l), new ExprVec(r)); }
  public static Expr binOp(String op, Vec l, double r)  { return new Expr(op, new ExprVec(l),new ExprNum(r)); }
  public static Expr binOp(String op, double l, Expr r) { return new Expr(op, new ExprNum(l),r); }
  public static Expr binOp(String op, double l, Vec r)  { return new Expr(op, new ExprNum(l), new ExprVec(r)); }
  // op(double, double) is not interesting...

  public static Expr unOp(String op, Expr e) { return new Expr(op, e); }
  public static Expr unOp(String op, Vec  v) { return new Expr(op, new ExprVec(v)); }

  private void compose(String op, Expr... exprs) {
    ArrayList<Expr> exprz = new ArrayList<>();
    Collections.addAll(exprz, exprs);
    _op=op;
    _exprs=exprz;
  }

  @Override protected Expr clone() {
    Expr e = new Expr();
    e._op = _op;
    e._exprs = new ArrayList<>();
    for(Expr expr: _exprs)
      e._exprs.add(expr.clone());
    return e;
  }

  public void bop(String op, Expr l, Expr r)   {
    boolean left;
    if( (left=this!=r) && this!=l )
      throw new IllegalArgumentException("expected this expr to appear in arguments.");
    compose(op,left?clone():l,left?r:clone());
  }

  public void bop(String op, Expr l, Vec r)    {
    if( this!=l )
      throw new IllegalArgumentException("expected expr to appear in args");
    compose(op,clone(),new ExprVec(r));
  }
  public void bop(String op, Expr l, double r) {
    if( this!=l )
      throw new IllegalArgumentException("expected expr to appear in args");
    compose(op,clone(),new ExprNum(r));
  }
  public void bop(String op, Vec l, Expr r)    {
    if( this!=r )
      throw new IllegalArgumentException("expected expr to appear in args");
    compose(op,new ExprVec(l),clone());
  }
  public void bop(String op, double l, Expr r) {
    if( this!=r )
      throw new IllegalArgumentException("expected expr to appear in args");
    compose(op,new ExprNum(l),clone());
  }
  // op(double, double) is not interesting...

  public void uop(String op) { compose(op,clone()); }

  /**
   * Construct a Rapids expression!
   * @return String that is a rapids expression
   */
  public String toRapids() {
    _argCnter.set(0);
    _argNames.clear();
    assignArgs();
    StringBuilder sb = constructArgs();
    return sb.append(asRapids()).append("}").toString();
  }

  StringBuilder constructArgs() {
    StringBuilder sb = new StringBuilder("{");
    for(String arg: _argNames.values())
      sb.append(" ").append(arg);
    sb.append(" ").append(".").append(" ");
    return sb;
  }

  StringBuilder asRapids() {
    StringBuilder sb = new StringBuilder("(");
    sb.append(_op);
    for(Expr expr: _exprs)
      sb.append(" ").append(expr.asRapids());
    sb.append(")");
    return sb;
  }

  void assignArgs() {
    for(Expr expr: _exprs)
      expr.assignArgs();
  }
}

class ExprNum extends Expr {
  double _d;
  ExprNum(double d) { _d=d; }
  @Override StringBuilder asRapids() { return new StringBuilder(""+_d); }
  @Override void assignArgs() { }
  @Override protected ExprNum clone() { return new ExprNum(_d); }
}

class ExprVec extends Expr {
  Vec _v;
  String _argName;
  ExprVec(Vec v) { _v=v; }
  @Override StringBuilder asRapids() { return new StringBuilder(_argName); }
  @Override void assignArgs() {
    _argName = _argNames.get(_v._key);
    if( null==_argName )
      _argNames.put(_v._key, _argName=ARGPREFIX+_argCnter.incrementAndGet());
  }
  @Override protected ExprVec clone() { return new ExprVec(_v); }
}