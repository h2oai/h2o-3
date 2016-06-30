package water.rapids.ast.prims.math;

/**
 */
public class AstCosPi extends AstUniOp {
  @Override
  public String str() {
    return "cospi";
  }

  @Override
  public double op(double d) {
    return Math.cos(Math.PI * d);
  }
}
