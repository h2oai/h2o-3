package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Natural logarithm function
 */
public class FnLog extends FnUniOp {

  public double apply(double x) {
    return FastMath.log(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "log");
  }
}
