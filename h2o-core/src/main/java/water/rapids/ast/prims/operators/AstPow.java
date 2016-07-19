package water.rapids.ast.prims.operators;

/**
 */
public class AstPow extends AstBinOp {
  public String str() {
    return "^";
  }

  public double op(double l, double r) {
    return Math.pow(l, r);
  }
}
