package ai.h2o.cascade.stdlib.operators;

import ai.h2o.cascade.core.GhostFrame;

/**
 * "Less than or equals" (&le;) operator.
 */
@SuppressWarnings("unused")  // loaded from StandardLibrary
public class FnLe extends FnBinOp {

  public boolean apply(double x, double y) {
    return x <= y;
  }

  public GhostFrame apply(double x, GhostFrame y) {
    return new NumericScalarFrameOp(x, y, LE);
  }

  public GhostFrame apply(GhostFrame x, double y) {
    return new NumericFrameScalarOp(x, y, LE);
  }

  public GhostFrame apply(GhostFrame x, GhostFrame y) {
    return numeric_frame_op_frame(x, y, LE);
  }


  private static BinLeSpec LE = new BinLeSpec();
  private static class BinLeSpec extends BinOpSpec {
    public BinLeSpec() {}
    public String name() { return "<="; }
    public double apply(double x, double y) { return x <= y? 1 : 0; }
  }
}
