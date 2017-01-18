package water.rapids.ast.prims.reducers;

import water.fvec.Frame;
import water.fvec.Vec;
import water.fvec.VecAry;
import water.rapids.Env;
import water.rapids.Val;
import water.rapids.vals.ValNum;
import water.rapids.vals.ValRow;
import water.rapids.ast.AstRoot;

/**
 * Optimization for the RollupStats: use them directly
 */
public abstract class AstRollupOp extends AstReducerOp {
  @Override
  public String[] args() {
    return new String[]{"ary"};
  }

  public abstract double rup(VecAry vec);

  @Override
  public Val apply(Env env, Env.StackHelp stk, AstRoot asts[]) {
    Val arg1 = asts[1].exec(env);
    if (arg1.isRow()) {        // Row-wise operation
      double[] ds = arg1.getRow();
      double d = ds[0];
      for (int i = 1; i < ds.length; i++)
        d = op(d, ds[i]);
      return new ValRow(new double[]{d}, null);
    }

    // Normal column-wise operation
    Frame fr = stk.track(arg1).getFrame();
    VecAry vecs = fr.vecs();
    if (vecs._numCols == 0 || vecs.naCnt() > 0) return new ValNum(Double.NaN);
    double d = rup(vecs.select(0));
    for (int i = 1; i < vecs._numCols; i++) {
      if (vecs.naCnt(i) > 0) return new ValNum(Double.NaN);
      d = op(d, rup(vecs.select(i)));
    }
    return new ValNum(d);
  }
}
