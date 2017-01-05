package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Square root function
 */
public class FnSqrt extends FnUniOp {

  public double apply(double x) {
    return FastMath.sqrt(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "sqrt");
  }
}
