package water.rapids.ast.prims.math;

import org.apache.commons.math3.util.FastMath;

/**
 */
public class AstAcosh extends AstUniOp {
  @Override
  public String str() {
    return "acosh";
  }

  @Override
  public double op(double d) {
    return FastMath.acosh(d);
  }
}
