package water.rapids.ast.prims.reducers;

import water.fvec.Vec;
import water.fvec.VecAry;

/**
 */
public class AstMaxNa extends AstNaRollupOp {
  public String str() {
    return "maxNA";
  }

  public double op(double l, double r) {
    return Math.max(l, r);
  }

  public double rup(VecAry vec) {
    return vec.max();
  }
}
