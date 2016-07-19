package water.rapids.ast.prims.math;

/**
 */
public class AstFloor extends AstUniOp {
  @Override
  public String str() {
    return "floor";
  }

  @Override
  public double op(double d) {
    return Math.floor(d);
  }
}
