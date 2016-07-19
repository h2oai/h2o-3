package water.rapids.ast.prims.math;

/**
 */
public class AstLog1P extends AstUniOp {
  @Override
  public String str() {
    return "log1p";
  }

  @Override
  public double op(double d) {
    return Math.log1p(d);
  }
}
