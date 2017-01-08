package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Cosine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnCos extends FnUniOp {

  public double apply(double x) {
    return FastMath.cos(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, COS);
  }


  private static CosSpec COS = new CosSpec();
  private static class CosSpec extends UniOpSpec {
    public CosSpec() {}
    @Override public String name() { return "cos"; }
    @Override public double apply(double x) { return FastMath.cos(x); }
  }
}
