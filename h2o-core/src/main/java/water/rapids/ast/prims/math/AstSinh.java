package water.rapids.ast.prims.math;

/**
 */
public class AstSinh extends AstUniOp {
  @Override
  public String str() {
    return "sinh";
  }

  @Override
  public double op(double d) {
    return Math.sinh(d);
  }
}
