package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Sine of the argument multiplied by Pi.
 */
public class FnSinPi extends FnUniOp {

  public double apply(double x) {
    return FastMath.sin(x * FastMath.PI);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "sinPi");
  }
}
