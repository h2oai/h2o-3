package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * Usual "plus" operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnPlus extends FnBinOp {

  public double apply(double x, double y) {
    return x + y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, ADD);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, ADD);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, ADD);
  }


  private static BinAddSpec ADD = new BinAddSpec();
  private static class BinAddSpec extends BinOpSpec {
    public BinAddSpec() {}
    public String name() { return "+"; }
    public double apply(double x, double y) { return x + y; }
  }
}
