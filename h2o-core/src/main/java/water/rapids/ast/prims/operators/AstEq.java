package water.rapids.ast.prims.operators;

import water.fvec.Frame;
import water.rapids.vals.ValFrame;
import water.util.MathUtils;
import water.etl.prims.operators.Eq;

/**
 */
public class AstEq extends AstBinOp {
  public String str() {
    return "==";
  }

  public double op(double l, double r) { // not really used here, just for implementing op method
    return MathUtils.equalsWithinOneSmallUlp(l, r) ? 1 : 0;
  }

  @Override
  public ValFrame frame_op_scalar(Frame fr, final double d) {
    return new ValFrame(Eq.get(fr,d));
  }

  @Override
  public boolean categoricalOK() {
    return true;
  }  // Make sense to run this OP on an enm?

}
