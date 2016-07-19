package water.rapids.ast.prims.math;

/**
 */
public class AstLog extends AstUniOp {
  @Override
  public String str() {
    return "log";
  }

  @Override
  public double op(double d) {
    return Math.log(d);
  }
}
