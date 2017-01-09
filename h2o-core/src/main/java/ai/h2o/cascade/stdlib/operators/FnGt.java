package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * "Greater than" (&gt;) operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnGt extends FnBinOp {

  public boolean apply(double x, double y) {
    return x > y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, GT);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, GT);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, GT);
  }


  private static BinGtSpec GT = new BinGtSpec();
  private static class BinGtSpec extends BinOpSpec {
    public BinGtSpec() {}
    public String name() { return ">"; }
    public double apply(double x, double y) { return x > y? 1 : 0; }
  }
}
