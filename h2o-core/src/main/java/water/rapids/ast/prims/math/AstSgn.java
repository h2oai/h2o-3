package water.rapids.ast.prims.math;

/**
 */
public class AstSgn extends AstUniOp {
  @Override
  public String str() {
    return "sign";
  }

  @Override
  public double op(double d) {
    return Math.signum(d);
  }
}
