package water.rapids.ast.prims.math;

import org.apache.commons.math3.special.Gamma;

/**
 */
public class AstLGamma extends AstUniOp {
  @Override
  public String str() {
    return "lgamma";
  }

  @Override
  public double op(double d) {
    return Gamma.logGamma(d);
  }
}
