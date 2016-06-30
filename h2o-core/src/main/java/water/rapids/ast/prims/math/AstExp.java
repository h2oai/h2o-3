package water.rapids.ast.prims.math;

/**
 */
public class AstExp extends AstUniOp {
  @Override
  public String str() {
    return "exp";
  }

  @Override
  public double op(double d) {
    return Math.exp(d);
  }
}
