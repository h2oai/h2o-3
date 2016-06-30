package water.rapids.ast.prims.operators;

/**
 * Subtraction
 */
public class AstSub extends AstBinOp {
  public String str() {
    return "-";
  }

  public double op(double l, double r) {
    return l - r;
  }
}
