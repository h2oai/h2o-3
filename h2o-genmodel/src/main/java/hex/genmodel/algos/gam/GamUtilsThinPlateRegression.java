package hex.genmodel.algos.gam;

public class GamUtilsThinPlateRegression {
  public static double calTPConstantTerm(int m, int d, boolean dEven) {
    if (dEven)
      return (Math.pow(-1, m + 1 + d / 2.0) / (Math.pow(2, 2*m - 1) * Math.pow(Math.PI, d / 2.0) * 
              factorial(m-1)*factorial(m - d / 2)));
    else
      return (Math.pow(-1, m) * m / (factorial(2 * m) * Math.pow(Math.PI, (d - 1) / 2.0)));
  }
  
  public static int factorial(int m) {
    if (m <= 1) {
      return 1;
    } else {
      int prod = 1;
      for (int index = 1; index <= m; index++)
        prod *= index;
      return prod;
    }
  }

  public static void calculateDistance(double[] rowValues, double[] chk, int knotNum, double[][] knots, int d, int m,
                                       boolean dEven, double constantTerms, double[] oneOGamColStd, boolean standardizeGAM) { // see 3.1
    for (int knotInd = 0; knotInd < knotNum; knotInd++) { // calculate distance between data and knots
      double sumSq = 0;
      for (int predInd = 0; predInd < d; predInd++) {
        double temp = standardizeGAM?(chk[predInd] - knots[predInd][knotInd])*oneOGamColStd[predInd]:
                (chk[predInd] - knots[predInd][knotInd]);  // standardized
        sumSq += temp*temp;
      }
      double distance = intPow(Math.sqrt(sumSq), 2 * m - d);
      rowValues[knotInd] = constantTerms*distance;
      if (dEven && (distance != 0))
        rowValues[knotInd] *= Math.log(distance);
    }
  }

  public static void calculatePolynomialBasis(double[] onePolyRow, double[] oneDataRow, int d, int M,
                                              int[][] polyBasisList, double[] gamColMean, double[] oneOGamStd, 
                                              boolean standardizeGAM) {
    for (int colIndex = 0; colIndex < M; colIndex++) {
      int[] oneBasis = polyBasisList[colIndex];
      double val = 1.0;
      for (int predIndex = 0; predIndex < d; predIndex++) {
        val *= intPow(standardizeGAM ? (oneDataRow[predIndex] - gamColMean[predIndex] * oneOGamStd[predIndex]) 
                                     : oneDataRow[predIndex],
                      oneBasis[predIndex]);
      }
      onePolyRow[colIndex] = val;
    }
  }

  /**
   * Deterministic integer power using explicit multiplication.
   * Avoids Math.pow JIT intrinsification which can produce different results
   * for Math.pow(x, 2) vs x*x (differing by 1 ULP for some values of x).
   * For our use-case the following is faster than StrictMath.pow() since GAM typically
   * uses low-order polynomials.
   *
   * For more details see:
   *     - https://bugs.openjdk.org/browse/JDK-8063086
   *     - https://bugs.openjdk.org/browse/JDK-8189172
   */
  public static double intPow(double base, int exp) {
    assert exp >= 0;
    switch (exp) {
      case 0: return 1.0;
      case 1: return base;
      case 2: return base * base;
      default:
        double result = base;
        for (int i = 1; i < exp; i++) result *= base;
        return result;
    }
  }
}
