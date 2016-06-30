package water.rapids.ast.prims.operators;

/**
 * Integer division
 */
public class AstIntDiv extends AstBinOp {
  public String str() {
    return "intDiv";
  }

  public double op(double l, double r) {
    return (((int) r) == 0) ? Double.NaN : (int) l / (int) r;
  }
}
