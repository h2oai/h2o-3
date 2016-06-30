package water.rapids.ast.prims.math;

import water.rapids.ast.prims.operators.AstBinOp;

/**
 */
public class AstSignif extends AstBinOp {
  public String str() {
    return "signif";
  }

  public double op(double x, double digits) {
    if (Double.isNaN(x)) return x;
    if (digits < 1) digits = 1; //mimic R's base::signif
    if ((int) digits != digits) digits = Math.round(digits);
    java.math.BigDecimal bd = new java.math.BigDecimal(x);
    bd = bd.round(new java.math.MathContext((int) digits, java.math.RoundingMode.HALF_EVEN));
    return bd.doubleValue();
  }
}
