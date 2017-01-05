package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Hyperbolic tangent function
 */
public class FnTanh extends FnUniOp {

  public double apply(double x) {
    return FastMath.tanh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "tanh");
  }
}
