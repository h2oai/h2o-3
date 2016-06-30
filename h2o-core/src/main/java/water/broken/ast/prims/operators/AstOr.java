package water.rapids.ast.prims.operators;

/**
 */
public class AstOr extends AstBinOp {
  public String str() {
    return "|";
  }

  public double op(double l, double r) {
    return AstLOr.or_op(l, r);
  }
}
