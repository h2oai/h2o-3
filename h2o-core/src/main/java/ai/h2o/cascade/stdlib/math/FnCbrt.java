package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Cubic root function
 */
public class FnCbrt extends FnUniOp {

  public double apply(double x) {
    return FastMath.cbrt(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "cbrt");
  }
}
