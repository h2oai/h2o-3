package water.rapids.ast.prims.reducers;

/**
 */
public class AstCumMin extends AstCumu {
  @Override
  public int nargs() {
    return 1 + 1;
  }

  @Override
  public String str() {
    return "cummin";
  }

  @Override
  public double op(double l, double r) {
    return Math.min(l, r);
  }

  @Override
  public double init() {
    return Double.MAX_VALUE;
  }
}
