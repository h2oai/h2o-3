package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Arc cosine function
 */
public class FnAcos extends FnUniOp {

  public double apply(double x) {
    return FastMath.acos(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "acos");
  }
}
