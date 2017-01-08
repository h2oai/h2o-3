package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.special.Erf;

/**
 * The <a href="http://mathworld.wolfram.com/Erf.html">error function</a>.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnErf extends FnUniOp {

  public double apply(double x) {
    return Erf.erf(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ERF);
  }


  private static ErfSpec ERF = new ErfSpec();
  private static class ErfSpec extends UniOpSpec {
    public ErfSpec() {}
    @Override public String name() { return "erf"; }
    @Override public double apply(double x) { return Erf.erf(x); }
  }
}
