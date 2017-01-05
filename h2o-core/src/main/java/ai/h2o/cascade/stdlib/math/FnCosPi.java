package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Cosine of the argument multiplied by Pi.
 */
public class FnCosPi extends FnUniOp {

  public double apply(double x) {
    return FastMath.cos(x * FastMath.PI);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "cosPi");
  }
}
