package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Gamma;

/**
 * Digamma function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnDigamma extends FnUniOp {

  public double apply(double x) {
    return x == x? Gamma.digamma(x) : Double.NaN;
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, DIGAMMA);
  }


  private static DigammaSpec DIGAMMA = new DigammaSpec();
  private static class DigammaSpec extends UniOpSpec {
    public DigammaSpec() {}
    @Override public String name() { return "digamma"; }
    @Override public double apply(double x) { return x == x? Gamma.digamma(x) : Double.NaN; }
  }
}
