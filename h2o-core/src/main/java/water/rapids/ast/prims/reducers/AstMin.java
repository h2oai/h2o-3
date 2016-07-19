package water.rapids.ast.prims.reducers;

import water.fvec.Vec;

/**
 */
public class AstMin extends AstRollupOp {
  public String str() {
    return "min";
  }

  public double op(double l, double r) {
    return Math.min(l, r);
  }

  public double rup(Vec vec) {
    return vec.min();
  }
}
