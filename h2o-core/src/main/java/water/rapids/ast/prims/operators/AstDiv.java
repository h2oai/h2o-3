package water.rapids.ast.prims.operators;

/**
 */
public class AstDiv extends AstBinOp {
  public String str() {
    return "/";
  }

  public double op(double l, double r) {
    return l / r;
  }
}
