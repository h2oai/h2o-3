package water.rapids.ast.prims.math;

import water.operations.Unary;

/**
 */
public class AstTrunc extends AstUniOp {
  @Override
  public String str() {
    return "trunc";
  }

  @Override
  public double op(double d) {
      return Unary.trunc(d);
  }

}
