package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Round to the nearest integer
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnTrunc extends FnUniOp {

  public double apply(double x) {
    return x >= 0? FastMath.floor(x) : FastMath.ceil(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, TRUNC);
  }


  private static TruncSpec TRUNC = new TruncSpec();
  private static class TruncSpec extends UniOpSpec {
    public TruncSpec() {}
    @Override public String name() { return "trunc"; }
    @Override public double apply(double x) { return x >= 0? FastMath.floor(x) : FastMath.ceil(x); }
  }
}
