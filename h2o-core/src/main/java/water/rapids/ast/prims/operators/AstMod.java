package water.rapids.ast.prims.operators;

/**
 * @see AstModR
 */
public class AstMod extends AstBinOp {
  public String str() {
    return "%";
  }

  public double op(double l, double r) {
    return l % r;
  }
}
