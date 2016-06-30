package water.rapids.ast.prims.math;

/**
 */
public class AstAtan extends AstUniOp {
  @Override
  public String str() {
    return "atan";
  }

  @Override
  public double op(double d) {
    return Math.atan(d);
  }
}
