package water.rapids.ast.prims.operators;

/**
 */
public class AstAnd extends AstBinOp {
  public String str() {
    return "&";
  }

  public double op(double l, double r) {
    return AstLAnd.and_op(l, r);
  }
}
