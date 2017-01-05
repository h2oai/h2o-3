package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Arctangent function
 */
public class FnAtan extends FnUniOp {

  public double apply(double x) {
    return FastMath.atan(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "atan");
  }
}
