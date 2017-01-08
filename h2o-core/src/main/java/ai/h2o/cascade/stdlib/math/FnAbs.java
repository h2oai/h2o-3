package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Absolute value of a number.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAbs extends FnUniOp {

  public double apply(double x) {
    return FastMath.abs(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ABS);
  }


  private static AbsSpec ABS = new AbsSpec();
  private static class AbsSpec extends UniOpSpec {
    public AbsSpec() {}
    @Override public String name() { return "abs"; }
    @Override public double apply(double x) { return FastMath.abs(x); }
  }

}
