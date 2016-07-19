package water.rapids.ast.prims.operators;

/**
 * Multiplication
 */
public class AstMul extends AstBinOp {
  public String str() {
    return "*";
  }

  public double op(double l, double r) {
    return l * r;
  }
}
