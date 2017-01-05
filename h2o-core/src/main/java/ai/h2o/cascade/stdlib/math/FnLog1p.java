package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * {@code log(1 + x)}
 */
public class FnLog1p extends FnUniOp {

  public double apply(double x) {
    return FastMath.log1p(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "log1p");
  }
}
