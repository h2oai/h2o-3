package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Inverse hyperbolic tangent function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAtanh extends FnUniOp {

  public double apply(double x) {
    return FastMath.atanh(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ATANH);
  }


  private static AtanhSpec ATANH = new AtanhSpec();
  private static class AtanhSpec extends UniOpSpec {
    public AtanhSpec() {}
    @Override public String name() { return "atanh"; }
    @Override public double apply(double x) { return FastMath.atanh(x); }
  }
}
