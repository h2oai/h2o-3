package water.rapids.ast.prims.math;

/**
 */
public class AstTan extends AstUniOp {
  @Override
  public String str() {
    return "tan";
  }

  @Override
  public double op(double d) {
    return Math.tan(d);
  }
}
