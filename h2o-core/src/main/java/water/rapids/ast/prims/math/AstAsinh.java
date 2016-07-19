package water.rapids.ast.prims.math;

import org.apache.commons.math3.util.FastMath;

/**
 */
public class AstAsinh extends AstUniOp {
  @Override
  public String str() {
    return "asinh";
  }

  @Override
  public double op(double d) {
    return FastMath.asinh(d);
  }
}
