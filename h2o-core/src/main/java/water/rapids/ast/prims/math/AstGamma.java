package water.rapids.ast.prims.math;

import org.apache.commons.math3.special.Gamma;

/**
 */
public class AstGamma extends AstUniOp {
  @Override
  public String str() {
    return "gamma";
  }

  @Override
  public double op(double d) {
    return Gamma.gamma(d);
  }
}
