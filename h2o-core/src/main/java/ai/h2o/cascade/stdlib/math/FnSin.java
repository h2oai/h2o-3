package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Sine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnSin extends FnUniOp {

  public double apply(double x) {
    return FastMath.sin(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, SIN);
  }


  private static SinSpec SIN = new SinSpec();
  private static class SinSpec extends UniOpSpec {
    public SinSpec() {}
    @Override public String name() { return "sin"; }
    @Override public double apply(double x) { return FastMath.sin(x); }
  }
}
