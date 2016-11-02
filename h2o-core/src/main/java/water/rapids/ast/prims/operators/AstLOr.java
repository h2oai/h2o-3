package water.rapids.ast.prims.operators;

import water.rapids.Env;
import water.rapids.Val;
import water.rapids.ast.AstRoot;

/**
 * Logical-OR.  If the first arg is true, do not execute the 2nd arg.
 */
public class AstLOr extends AstBinOp {
  public String str() {
    return "||";
  }

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val left = stk.track(asts[1].exec(env));
    // If the left is 1, just return the left
    if (left.isNum()) {
      double d = left.getNum();
      if (d == 1) return left;
    }
    Val rite = stk.track(asts[2].exec(env));
    return prim_apply(left, rite);
  }

  //  1 trumps NA, and NA trumps 0.
  public double op(double l, double r) {
    return or_op(l, r);
  }

  public static double or_op(double l, double r) {
    return (l == 1 || r == 1) ? 1 : (Double.isNaN(l) || Double.isNaN(r) ? Double.NaN : 0);
  }
}
