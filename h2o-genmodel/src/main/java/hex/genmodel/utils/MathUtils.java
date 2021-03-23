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

  /** 
   * Fast calculation of log base 2 for integers.
   *  @return log base 2 of n 
   */
  public static int log2(int n) {
    if (n <= 0) throw new IllegalArgumentException();
    return 31 - Integer.numberOfLeadingZeros(n);
  }
  public static int log2(long n) {
    return 63 - Long.numberOfLeadingZeros(n);
  }
  
  public static int combinatorial(int top, int bottom) {
    int denom = 1;
    int numer = 1;
    for (int index = 1; index <= bottom; index++) {
      numer *= (top - index + 1);
      denom *= index;
    }
    return (numer/denom);
  }
}
