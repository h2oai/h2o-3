package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Base-2 logarithm function
 */
public class FnLog2 extends FnUniOp {
  private static final double LOG2 = FastMath.log(2);

  public double apply(double x) {
    return FastMath.log(x) / LOG2;
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "log2");
  }
}
