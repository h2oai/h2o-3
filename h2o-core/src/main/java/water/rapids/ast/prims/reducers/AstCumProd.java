package water.rapids.ast.prims.reducers;

/**
 */
public class AstCumProd extends AstCumu {
  @Override
  public int nargs() {
    return 1 + 1;
  } // (cumprod x)

  @Override
  public String str() {
    return "cumprod";
  }

  @Override
  public double op(double l, double r) {
    return l * r;
  }

  @Override
  public double init() {
    return 1;
  }
}
