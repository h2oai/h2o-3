package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Erf;

/**
 * The <a href="http://mathworld.wolfram.com/Erf.html">error function</a>.
 */
public class FnErf extends FnUniOp {

  public double apply(double x) {
    return Erf.erf(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, "erf");
  }
}
