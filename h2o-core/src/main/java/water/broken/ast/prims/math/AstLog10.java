package water.rapids.ast.prims.math;

/**
 */
public class AstLog10 extends AstUniOp {
  @Override
  public String str() {
    return "log10";
  }

  @Override
  public double op(double d) {
    return Math.log10(d);
  }
}
