package water.rapids.ast.prims.math;

/**
 */
public class AstLog2 extends AstUniOp {
  @Override
  public String str() {
    return "log2";
  }

  @Override
  public double op(double d) {
    return Math.log(d) / Math.log(2);
  }
}
