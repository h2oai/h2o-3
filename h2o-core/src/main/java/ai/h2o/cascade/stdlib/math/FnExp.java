package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * The exponent function e^x
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnExp extends FnUniOp {

  public double apply(double x) {
    return FastMath.exp(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, EXP);
  }


  private static ExpSpec EXP = new ExpSpec();
  private static class ExpSpec extends UniOpSpec {
    public ExpSpec() {}
    @Override public String name() { return "exp"; }
    @Override public double apply(double x) { return FastMath.exp(x); }
  }
}
