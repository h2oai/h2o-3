package water.rapids.ast.prims.math;

import org.apache.commons.math3.util.FastMath;

/**
 */
public class AstAtanh extends AstUniOp {
  @Override
  public String str() {
    return "atanh";
  }

  @Override
  public double op(double d) {
    return FastMath.atanh(d);
  }
}
