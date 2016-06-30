package water.rapids.ast.prims.operators;

/**
 */
public class AstLe extends AstBinOp {
  public String str() {
    return "<=";
  }

  public double op(double l, double r) {
    return l <= r ? 1 : 0;
  }
}
