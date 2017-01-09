package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * "Less than" (&lt;) operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLt extends FnBinOp {

  public boolean apply(double x, double y) {
    return x < y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, LT);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, LT);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, LT);
  }


  private static BinLtSpec LT = new BinLtSpec();
  private static class BinLtSpec extends BinOpSpec {
    public BinLtSpec() {}
    public String name() { return "<"; }
    public double apply(double x, double y) { return x < y? 1 : 0; }
  }
}
