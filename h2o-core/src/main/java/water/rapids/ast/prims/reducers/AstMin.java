package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.fvec.VecAry;

/**
 */
public class AstMin extends AstRollupOp {
  public String str() {
    return "min";
  }

  public double op(double l, double r) {
    return Math.min(l, r);
  }

  public double rup(VecAry vec) {
    return vec.min();
  }
}
