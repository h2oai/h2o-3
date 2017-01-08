package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Hyperbolic tangent function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnTanh extends FnUniOp {

  public double apply(double x) {
    return FastMath.tanh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, TANH);
  }


  private static TanhSpec TANH = new TanhSpec();
  private static class TanhSpec extends UniOpSpec {
    public TanhSpec() {}
    @Override public String name() { return "tanh"; }
    @Override public double apply(double x) { return FastMath.tanh(x); }
  }
}
