package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Erf;

/**
 * The inverse error function.
 */
public class FnInvErf extends FnUniOp {

  public double apply(double x) {
    return Erf.erfInv(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "invErf");
  }
}
