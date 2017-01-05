package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Signum function (return the sign of the argument)
 */
public class FnSignum extends FnUniOp {

  public double apply(double x) {
    return FastMath.signum(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "signum");
  }
}
