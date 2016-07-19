package water.rapids.ast.prims.math;

/**
 */
public class AstNoOp extends AstUniOp {
  @Override
  public String str() {
    return "none";
  }

  @Override
  public double op(double d) {
    return d;
  }
}
