package water.rapids.ast.prims.math;

/**
 */
public class AstAcos extends AstUniOp {
  @Override
  public String str() {
    return "acos";
  }

  @Override
  public double op(double d) {
    return Math.acos(d);
  }
}
