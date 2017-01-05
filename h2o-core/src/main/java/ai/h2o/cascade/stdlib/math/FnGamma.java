package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Gamma;

/**
 * Gamma function
 */
public class FnGamma extends FnUniOp {

  public double apply(double x) {
    return x == x? Gamma.gamma(x) : Double.NaN;
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "gamma");
  }
}
