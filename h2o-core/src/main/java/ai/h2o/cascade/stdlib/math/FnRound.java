package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Round to the nearest integer
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnRound extends FnUniOp {

  public double apply(double x) {
    return FastMath.floor(x + 0.5);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ROUND);
  }


  private static RoundSpec ROUND = new RoundSpec();
  private static class RoundSpec extends UniOpSpec {
    public RoundSpec() {}
    @Override public String name() { return "round"; }
    @Override public double apply(double x) { return FastMath.floor(x + 0.5); }
  }
}
