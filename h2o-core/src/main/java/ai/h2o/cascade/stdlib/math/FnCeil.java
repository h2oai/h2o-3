package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Ceiling function (smallest whole number larger than x)
 */
public class FnCeil extends FnUniOp {

  public double apply(double x) {
    return FastMath.ceil(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "ceil");
  }
}
