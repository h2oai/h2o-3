package water.rapids.ast.prims.math;

/**
 */
public class AstCos extends AstUniOp {
  @Override
  public String str() {
    return "cos";
  }

  @Override
  public double op(double d) {
    return Math.cos(d);
  }
}
