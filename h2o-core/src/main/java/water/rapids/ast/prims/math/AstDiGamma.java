package water.rapids.ast.prims.math;

import water.operations.Unary;

/**
 */
public class AstDiGamma extends AstUniOp {
  @Override
  public String str() {
    return "digamma";
  }

  @Override
  public double op(double d) {
      return Unary.DiGamma(d);
  }

}
