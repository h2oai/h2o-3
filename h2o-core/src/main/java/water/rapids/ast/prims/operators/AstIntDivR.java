package water.rapids.ast.prims.operators;

/**
 * Language R intdiv op
 */
public class AstIntDivR extends AstBinOp {
  public String str() {
    return "%/%";
  }

  public double op(double l, double r) {
    return (int) (l / r);
  }
}
