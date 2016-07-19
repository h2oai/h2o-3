package water.rapids.ast.prims.reducers;

import water.fvec.Vec;

/**
 */
public class AstMaxNa extends AstNaRollupOp {
  public String str() {
    return "maxNA";
  }

  public double op(double l, double r) {
    return Math.max(l, r);
  }

  public double rup(Vec vec) {
    return vec.max();
  }
}
