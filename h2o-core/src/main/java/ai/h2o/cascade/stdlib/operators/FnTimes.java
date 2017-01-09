package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * Multiplication operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnTimes extends FnBinOp {

  public double apply(double x, double y) {
    return x * y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, MUL);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, MUL);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, MUL);
  }


  private static BinMulSpec MUL = new BinMulSpec();
  private static class BinMulSpec extends BinOpSpec {
    public BinMulSpec() {}
    public String name() { return "*"; }
    public double apply(double x, double y) { return x * y; }
  }
}
