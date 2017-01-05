package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Decimal logarithm function
 */
public class FnLog10 extends FnUniOp {

  public double apply(double x) {
    return FastMath.log10(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "log10");
  }

}
