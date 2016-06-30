package water.rapids.ast.prims.math;

/**
 */
public class AstSqrt extends AstUniOp {
  @Override
  public String str() {
    return "sqrt";
  }

  @Override
  public double op(double d) {
    return Math.sqrt(d);
  }
}
