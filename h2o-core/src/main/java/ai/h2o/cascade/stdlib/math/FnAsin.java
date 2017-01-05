package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Arc sine function
 */
public class FnAsin extends FnUniOp {

  public double apply(double x) {
    return FastMath.asin(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "asin");
  }
}
