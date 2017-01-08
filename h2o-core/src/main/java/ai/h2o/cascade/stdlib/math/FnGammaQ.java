package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Gamma;

/**
 * Regularized Gamma Q function (http://mathworld.wolfram.com/RegularizedGammaFunction.html)
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnGammaQ extends FnUniOp {

  public double apply(double x, double a) {
    return Gamma.regularizedGammaQ(a, x);
  }

  public GhostFrame apply(GhostFrame frame, double a) {
    if (Double.isNaN(a) || a <= 0)
      return new NanFrame(frame, "gammaQ");
    else
      return new NumericUniOpFrame(frame, new GammaQSpec(a));
  }


  private static class GammaQSpec extends UniOpSpec {
    private double a;
    public GammaQSpec() {}  // for Externalizable interface
    public GammaQSpec(double a ) { this.a = a; }
    @Override public String name() { return "gammaQ"; }
    @Override public double apply(double x) { return Gamma.regularizedGammaQ(a, x); }
  }
}
