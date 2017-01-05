package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * The exponent function e^x
 */
public class FnExp extends FnUniOp {

  public double apply(double x) {
    return FastMath.exp(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "exp");
  }
}
