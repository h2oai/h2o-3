package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Inverse hyperbolic cosine function
 */
public class FnAcosh extends FnUniOp {

  public double apply(double x) {
    return FastMath.acosh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "acosh");
  }
}
