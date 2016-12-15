package water.rapids.ast.prims.math;


import water.operations.Unary;

/**
 */
public class AstGamma extends AstUniOp {
  @Override
  public String str() {
    return "gamma";
  }

  @Override
  public double op(double d) {
      return Unary.gamma(d);
  }
}
