package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Sine function
 */
public class FnSin extends FnUniOp {

  public double apply(double x) {
    return FastMath.sin(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "sin");
  }
}
