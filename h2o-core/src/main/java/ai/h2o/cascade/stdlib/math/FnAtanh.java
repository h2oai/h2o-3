package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Inverse hyperbolic tangent function
 */
public class FnAtanh extends FnUniOp {

  public double apply(double x) {
    return FastMath.atanh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "atanh");
  }
}
