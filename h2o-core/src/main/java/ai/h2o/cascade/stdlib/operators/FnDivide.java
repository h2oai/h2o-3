package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * Division operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnDivide extends FnBinOp {

  public double apply(double x, double y) {
    return x / y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, DIV);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, DIV);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, DIV);
  }


  private static BinDivSpec DIV = new BinDivSpec();
  private static class BinDivSpec extends BinOpSpec {
    public BinDivSpec() {}
    public String name() { return "/"; }
    public double apply(double x, double y) { return x / y; }
  }
}
