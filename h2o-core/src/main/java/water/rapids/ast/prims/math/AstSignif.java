package water.rapids.ast.prims.math;

import water.operations.Unary;
import water.rapids.ast.prims.operators.AstBinOp;

/**
 */
public class AstSignif extends AstBinOp {
  public String str() {
    return "signif";
  }

  public double op(double x, double digits) {
      return Unary.signif(x, digits);
  }

}
