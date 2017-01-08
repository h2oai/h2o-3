package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Decimal logarithm function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLog10 extends FnUniOp {

  public double apply(double x) {
    return FastMath.log10(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, LOG10);
  }


  private static Log10Spec LOG10 = new Log10Spec();
  private static class Log10Spec extends UniOpSpec {
    public Log10Spec() {}
    @Override public String name() { return "log10"; }
    @Override public double apply(double x) { return FastMath.log10(x); }
  }
}
