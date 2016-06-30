package water.rapids.ast.prims.math;

/**
 */
public class AstTanh extends AstUniOp {
  @Override
  public String str() {
    return "tanh";
  }

  @Override
  public double op(double d) {
    return Math.tanh(d);
  }
}
