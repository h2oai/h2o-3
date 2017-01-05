package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Tangent function
 */
public class FnTan extends FnUniOp {

  public double apply(double x) {
    return FastMath.tan(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "tan");
  }
}
