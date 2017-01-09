package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * The "minus" operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnMinus extends FnBinOp {

  public double apply(double x, double y) {
    return x - y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, MINUS);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, MINUS);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, MINUS);
  }


  private static BinSubSpec MINUS = new BinSubSpec();
  private static class BinSubSpec extends BinOpSpec {
    public BinSubSpec() {}
    public String name() { return "-"; }
    public double apply(double x, double y) { return x - y; }
  }
}
