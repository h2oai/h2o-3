package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Gamma;

/**
 * Log-Gamma function for positive arguments.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLogGamma extends FnUniOp {

  public double apply(double x) {
    return x == x? Gamma.logGamma(x) : Double.NaN;
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, LOGGAMMA);
  }


  private static LogGammaSpec LOGGAMMA = new LogGammaSpec();
  private static class LogGammaSpec extends UniOpSpec {
    public LogGammaSpec() {}
    @Override public String name() { return "logGamma"; }
    @Override public double apply(double x) { return x == x? Gamma.logGamma(x) : Double.NaN; }
  }
}
