package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Cubic root function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnCbrt extends FnUniOp {

  public double apply(double x) {
    return FastMath.cbrt(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, CBRT);
  }


  private static CbrtSpec CBRT = new CbrtSpec();
  private static class CbrtSpec extends UniOpSpec {
    public CbrtSpec() {}
    @Override public String name() { return "cbrt"; }
    @Override public double apply(double x) { return FastMath.cbrt(x); }
  }
}
