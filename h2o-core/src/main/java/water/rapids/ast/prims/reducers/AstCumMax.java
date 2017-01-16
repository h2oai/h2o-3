package water.rapids.ast.prims.reducers;


public class AstCumMax extends AstCumu {
  @Override
  public int nargs() { return 1 + 2; }

  @Override
  public String str() {
    return "cummax";
  }

  @Override
  public double op(double l, double r) {
    return Math.max(l, r);
  }

  @Override
  public double init() {
    return -Double.MAX_VALUE;
  }
}
