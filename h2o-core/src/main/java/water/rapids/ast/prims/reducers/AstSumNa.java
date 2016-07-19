package water.rapids.ast.prims.reducers;

import water.fvec.Vec;

/**
 */
public class AstSumNa extends AstNaRollupOp {
  public String str() {
    return "sumNA";
  }

  public double op(double l, double r) {
    return l + r;
  }

  public double rup(Vec vec) {
    return vec.mean() * (vec.length() - vec.naCnt());
  }
}
