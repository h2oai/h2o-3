package water.rapids.ast.prims.math;

import org.apache.commons.math3.special.Gamma;

/**
 */
public class AstTriGamma extends AstUniOp {
  @Override
  public String str() {
    return "trigamma";
  }

  @Override
  public double op(double d) {
    return Double.isNaN(d) ? Double.NaN : Gamma.trigamma(d);
  }
}
