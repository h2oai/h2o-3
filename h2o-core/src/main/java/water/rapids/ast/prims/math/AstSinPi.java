package water.rapids.ast.prims.math;

/**
 */
public class AstSinPi extends AstUniOp {
  @Override
  public String str() {
    return "sinpi";
  }

  @Override
  public double op(double d) {
    return Math.sin(Math.PI * d);
  }
}
