package water.rapids.ast.prims.math;

/**
 */
public class AstCeiling extends AstUniOp {
  @Override
  public String str() {
    return "ceiling";
  }

  @Override
  public double op(double d) {
    return Math.ceil(d);
  }
}
