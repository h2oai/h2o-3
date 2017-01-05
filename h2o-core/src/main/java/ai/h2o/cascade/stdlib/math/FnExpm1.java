package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Function {@code e^x - 1}
 */
public class FnExpm1 extends FnUniOp {

  public double apply(double x) {
    return FastMath.expm1(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "expm1");
  }
}
