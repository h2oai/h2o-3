package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.rapids.Env;
import water.rapids.ast.Ast;
import water.rapids.vals.ValNum;

/**
 * Optimization for the RollupStats: use them directly
 */
public abstract class AstNaRollupOp extends AstRollupOp {
  @Override
  public ValNum apply(Env env, Env.StackHelp stk, Ast asts[]) {
    Frame fr = stk.track(asts[1].exec(env)).getFrame();
    Vec[] vecs = fr.vecs();
    if (vecs.length == 0) return new ValNum(Double.NaN);
    double d = rup(vecs[0]);
    for (int i = 1; i < vecs.length; i++)
      d = op(d, rup(vecs[i]));
    return new ValNum(d);
  }
}
