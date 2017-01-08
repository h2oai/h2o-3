package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Arctangent function
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnAtan extends FnUniOp {

  public double apply(double x) {
    return FastMath.atan(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, ATAN);
  }


  private static AtanSpec ATAN = new AtanSpec();
  private static class AtanSpec extends UniOpSpec {
    public AtanSpec() {}
    @Override public String name() { return "atan"; }
    @Override public double apply(double x) { return FastMath.atan(x); }
  }
}
