package water.rapids.ast.prims.reducers;

import water.fvec.Vec;

/**
 * Subclasses take a Frame and produces a scalar.  NAs are dropped
 */
//abstract class ASTNARedOp extends AstReducerOp {
//  @Override ValNum apply( Env env, Env.StackHelp stk, AstRoot asts[] ) {
//    Frame fr = stk.track(asts[1].exec(env)).getFrame();
//    return new ValNum(new NaRmRedOp().doAll(fr)._d);
//  }
//}

public class AstMinNa extends AstNaRollupOp {
  public String str() {
    return "minNA";
  }

  public double op(double l, double r) {
    return Math.min(l, r);
  }

  public double rup(Vec vec) {
    return vec.min();
  }
}
