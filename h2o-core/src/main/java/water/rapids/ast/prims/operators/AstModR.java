package water.rapids.ast.prims.operators;

/**
 * Language R mod operator
 */
public class AstModR extends AstBinOp {
  public String str() {
    return "%%";
  }

  public double op(double l, double r) {
    return l % r;
  }
}
