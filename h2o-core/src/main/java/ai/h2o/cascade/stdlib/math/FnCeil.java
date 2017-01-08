package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Ceiling function (smallest whole number larger than x)
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnCeil extends FnUniOp {

  public double apply(double x) {
    return FastMath.ceil(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, CEIL);
  }


  private static CeilSpec CEIL = new CeilSpec();
  private static class CeilSpec extends UniOpSpec {
    public CeilSpec() {}
    @Override public String name() { return "ceil"; }
    @Override public double apply(double x) { return FastMath.ceil(x); }
  }
}
