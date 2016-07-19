package water.rapids.ast.prims.math;

import water.rapids.ast.prims.operators.AstBinOp;

/**
 */
public class AstRound extends AstBinOp {
  public String str() {
    return "round";
  }

  public double op(double x, double digits) {
    // e.g.: floor(2.676*100 + 0.5) / 100 => 2.68
    if (Double.isNaN(x)) return x;
    double sgn = x < 0 ? -1 : 1;
    x = Math.abs(x);
    if ((int) digits != digits) digits = Math.round(digits);
    double power_of_10 = (int) Math.pow(10, (int) digits);
    return sgn * (digits == 0
        // go to the even digit
        ? (x % 1 > 0.5 || (x % 1 == 0.5 && !(Math.floor(x) % 2 == 0)))
        ? Math.ceil(x)
        : Math.floor(x)
        : Math.floor(x * power_of_10 + 0.5) / power_of_10);
  }
}
