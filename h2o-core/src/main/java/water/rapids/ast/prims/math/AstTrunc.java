package water.rapids.ast.prims.math;

/**
 */
public class AstTrunc extends AstUniOp {
  @Override
  public String str() {
    return "trunc";
  }

  @Override
  public double op(double d) {
    return d >= 0 ? Math.floor(d) : Math.ceil(d);
  }
}
