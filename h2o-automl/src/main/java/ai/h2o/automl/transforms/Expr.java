package ai.h2o.automl.transforms;

import water.Key;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.TransformWrappedVec;
import water.fvec.Vec;
import water.rapids.Rapids;
import water.rapids.ast.AstFunction;
import water.rapids.ast.AstPrimitive;
import water.rapids.ast.AstRoot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

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
public class Expr implements Cloneable {
  private ArrayList<Expr> _exprs; // dag
  private String _op;             // opcode string ("+", "log", etc.)
  static final String ARGPREFIX="x";                       // the arg prefix  { x1 x2 x3 .
  static HashMap<Key, String> _argNames = new HashMap<>(); // mapping of Vec keys to arg names
  static AtomicInteger _argCnter = new AtomicInteger(0);   // argument counter, for labeling args nicely
  protected Vec _anyVec;                                   // any random vec to be used for constructing the TransformWrappedVec
  static ArrayList<Key<Vec>> _vecs = new ArrayList<>();    // order of vecs, used for TransformWrappedVec making

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


  // these three bops are for flow coding
  public Expr bop(String op, Vec r)    { bop(op,this,r); return this; }
  public Expr bop(String op, Expr r)   { bop(op,this,r); return this; }
  public Expr bop(String op, double r) { bop(op,this,r); return this; }

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
    _vecs.clear();
    assignArgs();
    _anyVec = _vecs.get(0).get();
    StringBuilder sb = constructArgs();
    return sb.append(asRapids()).append("}").toString();
  }

  public TransformWrappedVec toWrappedVec() {
    // AstRoot fun = new AstExec(toRapids()).parse();
    AstRoot root = Rapids.parse(toRapids());

    if (! (root instanceof AstPrimitive))
      throw new H2OIllegalArgumentException("Internal error in transform engine", "Internal error in transform engine: expected the transform expression to parse as an AstPrimitive, but it's a: " + root.getClass() + ": " + toRapids());

    AstPrimitive fun = (AstPrimitive)root;
    return new TransformWrappedVec(_anyVec.group().addVec(), _anyVec._rowLayout, fun, _vecs.toArray(new Key[_vecs.size()]));
  }

  private StringBuilder constructArgs() {
    StringBuilder sb = new StringBuilder("{");
    for(int i=1;i<=_argCnter.get();++i)
      sb.append(" ").append(ARGPREFIX).append(i);
    sb.append(" ").append(".").append(" ");
    return sb;
  }

  protected StringBuilder asRapids() {
    StringBuilder sb = new StringBuilder("(");
    sb.append(_op);
    for(Expr expr: _exprs)
      sb.append(" ").append(expr.asRapids());
    sb.append(")");
    return sb;
  }

  protected void assignArgs() {
    for(Expr expr: _exprs)
      expr.assignArgs();
  }
}

class ExprNum extends Expr {
  double _d;
  ExprNum(double d) { _d=d; }
  @Override protected StringBuilder asRapids() { return new StringBuilder(""+_d); }
  @Override protected void assignArgs() { }
  @Override protected ExprNum clone() { return new ExprNum(_d); }
}

class ExprVec extends Expr {
  Vec _v;
  String _argName;
  ExprVec(Vec v) { _v=v; }
  @Override protected StringBuilder asRapids() { return new StringBuilder(_argName); }
  @Override protected void assignArgs() {
    _argName = _argNames.get(_v._key);
    if( null==_argName ) {
      _argNames.put(_v._key, _argName = ARGPREFIX + _argCnter.incrementAndGet());
      _vecs.add(_v._key);
    }
  }
  @Override protected ExprVec clone() { return new ExprVec(_v); }
}