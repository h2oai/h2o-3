package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * "Greater than or equals" (&ge;) operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnGe extends FnBinOp {

  public boolean apply(double x, double y) {
    return x >= y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, GE);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, GE);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, GE);
  }


  private static BinGeSpec GE = new BinGeSpec();
  private static class BinGeSpec extends BinOpSpec {
    public BinGeSpec() {}
    public String name() { return ">="; }
    public double apply(double x, double y) { return x >= y? 1 : 0; }
  }
}
