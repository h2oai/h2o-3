package ai.h2o.cascade.stdlib.math;

import ai.h2o.cascade.core.GhostFrame;
import org.apache.commons.math3.util.FastMath;

/**
 * Floor function (largest whole number not exceeding x)
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnFloor extends FnUniOp {

  public double apply(double x) {
    return FastMath.floor(x);
  }

  public GhostFrame apply(GhostFrame frame) {
    return new NumericUniOpFrame(frame, FLOOR);
  }


  private static FloorSpec FLOOR = new FloorSpec();
  private static class FloorSpec extends UniOpSpec {
    public FloorSpec() {}
    @Override public String name() { return "floor"; }
    @Override public double apply(double x) { return FastMath.floor(x); }
  }
}
