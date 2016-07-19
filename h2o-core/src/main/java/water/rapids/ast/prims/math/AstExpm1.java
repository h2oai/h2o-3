package water.rapids.ast.prims.math;

/**
 */
public class AstExpm1 extends AstUniOp {
  @Override
  public String str() {
    return "expm1";
  }

  @Override
  public double op(double d) {
    return Math.expm1(d);
  }
}
