package water.rapids.ast.prims.reducers;

/**
 */
public class AstCumSum extends AstCumu {
  @Override
  public int nargs() {
    return 1 + 1;
  } // (cumsum x)

  @Override
  public String str() {
    return "cumsum";
  }

  @Override
  public double op(double l, double r) {
    return l + r;
  }

  @Override
  public double init() {
    return 0;
  }
}
