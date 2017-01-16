package hex.genmodel.utils;

/**
 * Copied (partially) from water.util.MathUtils
 */
public class MathUtils {

  // Section 4.2: Error bound on recursive sum from Higham, Accuracy and Stability of Numerical Algorithms, 2nd Ed
  // |E_n| <= (n-1) * u * \sum_i^n |x_i| + P(u^2)
  public static boolean equalsWithinRecSumErr(double actual, double expected, int n, double absum) {
    return Math.abs(actual - expected) <= (n-1) * Math.ulp(actual) * absum;
  }

}
