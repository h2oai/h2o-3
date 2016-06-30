package water.rapids.ast.prims.math;

/**
 */
public class AstAbs extends AstUniOp {
  @Override
  public String str() {
    return "abs";
  }

  @Override
  public double op(double d) {
    return Math.abs(d);
  }
}
