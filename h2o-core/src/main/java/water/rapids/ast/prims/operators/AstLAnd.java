package water.rapids.ast.prims.operators;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;

/**
 * Logical-AND.  If the first arg is false, do not execute the 2nd arg.
 */
public class AstLAnd extends AstBinOp {
  public String str() {
    return "&&";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val left = stk.track(asts[1].exec(env));
    // If the left is zero, just return the left
    if (left.isNum()) {
      double d = left.getNum();
      if (d == 0) return left;
    }
    Val rite = stk.track(asts[2].exec(env));
    return prim_apply(left, rite);
  }

  // 0 trumps NA, and NA trumps 1
  public double op(double l, double r) {
    return and_op(l, r);
  }

  public static double and_op(double l, double r) {
    return (l == 0 || r == 0) ? 0 : (Double.isNaN(l) || Double.isNaN(r) ? Double.NaN : 1);
  }
}
