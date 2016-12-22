package water.rapids.ast.prims.math;

import water.operations.Unary;
import water.rapids.ast.prims.operators.AstBinOp;

/**
 */
public class AstRound extends AstBinOp {
  public String str() {
    return "round";
  }

  public double op(double x, double digits) {
      return Unary.round(x, digits);

  }

}
