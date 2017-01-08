package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Function {@code e^x - 1}
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnExpm1 extends FnUniOp {

  public double apply(double x) {
    return FastMath.expm1(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, EXPM1);
  }


  private static Expm1Spec EXPM1 = new Expm1Spec();
  private static class Expm1Spec extends UniOpSpec {
    public Expm1Spec() {}
    @Override public String name() { return "expm1"; }
    @Override public double apply(double x) { return FastMath.expm1(x); }
  }
}
