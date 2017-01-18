package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.fvec.VecAry;

/**
 */
public class AstMax extends AstRollupOp {
  public String str() {
    return "max";
  }

  public double op(double l, double r) {
    return Math.max(l, r);
  }

  public double rup(VecAry vec) {
    return vec.max();
  }
}
