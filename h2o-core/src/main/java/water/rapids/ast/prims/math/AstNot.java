package water.rapids.ast.prims.math;

/**
 */
public class AstNot extends AstUniOp {
  public String str() {
    return "not";
  }

  public double op(double d) {
    return Double.isNaN(d) ? Double.NaN : d == 0 ? 1 : 0;
  }
}
