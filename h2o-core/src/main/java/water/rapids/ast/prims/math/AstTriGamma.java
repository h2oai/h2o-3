package water.rapids.ast.prims.math;

import water.operations.Unary;

/**
 */
public class AstTriGamma extends AstUniOp {
  @Override
  public String str() {
    return "trigamma";
  }

  @Override
  public double op(double d) {
      return Unary.trigamma(d);
  }

}
