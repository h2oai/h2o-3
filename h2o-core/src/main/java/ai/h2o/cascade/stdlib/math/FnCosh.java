package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Hyperbolic cosine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnCosh extends FnUniOp {

  public double apply(double x) {
    return FastMath.cosh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, COSH);
  }


  private static CoshSpec COSH = new CoshSpec();
  private static class CoshSpec extends UniOpSpec {
    public CoshSpec() {}
    @Override public String name() { return "cosh"; }
    @Override public double apply(double x) { return FastMath.cosh(x); }
  }
}
