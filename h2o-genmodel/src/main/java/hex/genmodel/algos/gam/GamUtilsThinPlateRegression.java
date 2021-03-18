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
      double distance = Math.pow(Math.sqrt(sumSq), 2*m-d);
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
        val *= standardizeGAM?
                Math.pow((oneDataRow[predIndex]-gamColMean[predIndex]*oneOGamStd[predIndex]), oneBasis[predIndex]):
                Math.pow(oneDataRow[predIndex], oneBasis[predIndex]);
      }
      onePolyRow[colIndex] = val;
    }
  }
}
