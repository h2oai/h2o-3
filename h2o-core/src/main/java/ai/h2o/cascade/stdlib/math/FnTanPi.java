package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Tangent of the argument multiplied by Pi.
 */
public class FnTanPi extends FnUniOp {

  public double apply(double x) {
    return FastMath.tan(x * FastMath.PI);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "tanPi");
  }
}
