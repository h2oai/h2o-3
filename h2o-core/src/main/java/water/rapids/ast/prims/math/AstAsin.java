package water.rapids.ast.prims.math;

/**
 */
public class AstAsin extends AstUniOp {
  @Override
  public String str() {
    return "asin";
  }

  @Override
  public double op(double d) {
    return Math.asin(d);
  }
}
