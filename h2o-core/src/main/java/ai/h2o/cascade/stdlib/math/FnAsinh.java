package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Inverse hyperbolic sine function
 */
public class FnAsinh extends FnUniOp {

  public double apply(double x) {
    return FastMath.asinh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "asinh");
  }
}
