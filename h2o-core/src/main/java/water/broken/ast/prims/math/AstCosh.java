package water.rapids.ast.prims.math;

/**
 */
public class AstCosh extends AstUniOp {
  @Override
  public String str() {
    return "cosh";
  }

  @Override
  public double op(double d) {
    return Math.cosh(d);
  }
}
