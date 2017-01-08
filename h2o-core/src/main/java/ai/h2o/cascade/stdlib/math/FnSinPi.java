package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Sine of the argument multiplied by Pi.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnSinPi extends FnUniOp {

  public double apply(double x) {
    return FastMath.sin(x * FastMath.PI);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, SINPI);
  }


  private static SinPiSpec SINPI = new SinPiSpec();
  private static class SinPiSpec extends UniOpSpec {
    public SinPiSpec() {}
    @Override public String name() { return "sinh"; }
    @Override public double apply(double x) { return FastMath.sin(x * FastMath.PI); }
  }
}
