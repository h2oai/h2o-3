package water.rapids.ast.prims.math;

/**
 */
public class AstSin extends AstUniOp {
  @Override
  public String str() {
    return "sin";
  }

  @Override
  public double op(double d) {
    return Math.sin(d);
  }
}
