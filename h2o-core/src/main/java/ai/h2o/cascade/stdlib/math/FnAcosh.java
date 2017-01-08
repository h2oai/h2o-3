package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Inverse hyperbolic cosine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAcosh extends FnUniOp {

  public double apply(double x) {
    return FastMath.acosh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ACOSH);
  }


  private static AcoshSpec ACOSH = new AcoshSpec();
  private static class AcoshSpec extends UniOpSpec {
    public AcoshSpec() {}
    @Override public String name() { return "acosh"; }
    @Override public double apply(double x) { return FastMath.acosh(x); }
  }
}
