package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Arc cosine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAcos extends FnUniOp {

  public double apply(double x) {
    return FastMath.acos(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ACOS);
  }


  private static AcosSpec ACOS = new AcosSpec();
  private static class AcosSpec extends UniOpSpec {
    public AcosSpec() {}
    @Override public String name() { return "acos"; }
    @Override public double apply(double x) { return FastMath.acos(x); }
  }
}
