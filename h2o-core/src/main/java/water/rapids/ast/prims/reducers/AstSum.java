package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.fvec.VecAry;

/**
 */
public class AstSum extends AstRollupOp {
  public String str() {
    return "sum";
  }

  public double op(double l, double r) {
    return l + r;
  }

  public double rup(VecAry vec) {
    return vec.mean() * vec.length();
  }
}
