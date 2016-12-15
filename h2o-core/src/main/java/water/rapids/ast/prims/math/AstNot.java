package water.rapids.ast.prims.math;

import water.operations.Unary;

/**
 */
public class AstNot extends AstUniOp {
  public String str() {
    return "not";
  }

  public double op(double d) {
      return Unary.Not(d);
  }

}
