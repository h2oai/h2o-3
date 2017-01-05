package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Erf;

/**
 * The complementary error function: {@code erfc(x) = 1 - erf(x)}.
 */
public class FnErfc extends FnUniOp {

  public double apply(double x) {
    return Erf.erfc(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "erfc");
  }
}
