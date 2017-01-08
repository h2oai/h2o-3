package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Gamma;

/**
 * Regularized Gamma P function (http://mathworld.wolfram.com/RegularizedGammaFunction.html)
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnGammaP extends FnUniOp {

  public double apply(double x, double a) {
    return Gamma.regularizedGammaP(a, x);
  }

  public GhostFrame apply(GhostFrame frame, double a) {
    if (Double.isNaN(a) || a <= 0)
      return new NanFrame(frame, "gammaP");
    else
      return new NumericUniOpFrame(frame, new GammaPSpec(a));
  }


  private static class GammaPSpec extends UniOpSpec {
    private double a;
    public GammaPSpec() {}  // for Externalizable interface
    public GammaPSpec(double a ) { this.a = a; }
    @Override public String name() { return "gammaP"; }
    @Override public double apply(double x) { return Gamma.regularizedGammaP(a, x); }
  }
}
