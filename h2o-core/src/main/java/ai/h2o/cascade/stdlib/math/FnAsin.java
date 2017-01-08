package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Arc sine function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAsin extends FnUniOp {

  public double apply(double x) {
    return FastMath.asin(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ASIN);
  }


  private static AsinSpec ASIN = new AsinSpec();
  private static class AsinSpec extends UniOpSpec {
    public AsinSpec() {}
    @Override public String name() { return "asin"; }
    @Override public double apply(double x) { return FastMath.asin(x); }
  }
}
