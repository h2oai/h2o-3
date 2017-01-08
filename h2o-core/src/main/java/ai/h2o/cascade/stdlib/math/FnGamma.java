package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Gamma;

/**
 * Gamma function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnGamma extends FnUniOp {

  public double apply(double x) {
    return x == x? Gamma.gamma(x) : Double.NaN;
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, GAMMA);
  }


  private static GammaSpec GAMMA = new GammaSpec();
  private static class GammaSpec extends UniOpSpec {
    public GammaSpec() {}
    @Override public String name() { return "gamma"; }
    @Override public double apply(double x) { return x == x? Gamma.gamma(x) : Double.NaN; }
  }
}
