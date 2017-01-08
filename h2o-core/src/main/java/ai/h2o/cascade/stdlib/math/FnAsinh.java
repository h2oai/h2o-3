package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Inverse hyperbolic sine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAsinh extends FnUniOp {

  public double apply(double x) {
    return FastMath.asinh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ASINH);
  }


  private static AsinhSpec ASINH = new AsinhSpec();
  private static class AsinhSpec extends UniOpSpec {
    public AsinhSpec() {}
    @Override public String name() { return "asinh"; }
    @Override public double apply(double x) { return FastMath.asinh(x); }
  }
}
