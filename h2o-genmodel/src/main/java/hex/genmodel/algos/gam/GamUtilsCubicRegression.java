package hex.genmodel.algos.gam;

import java.util.Arrays;

public class GamUtilsCubicRegression {
  public static double gen_a_m_j(double xjp1, double x, double hj) {
    return (xjp1-x)/hj;
  }

  public static double gen_a_p_j(double xj, double x, double hj) {
    return (x-xj)/hj;
  }

  public static double gen_c_m_j(double xjp1, double x, double hj) {
    double t = (xjp1-x);
    double t3 = t*t*t;
    return ((t3/hj-t*hj)/6.0);
  }

  public static double gen_c_p_j(double xj, double x, double hj) {
    double t=(x-xj);
    double t3 = t*t*t;
    return ((t3/hj-t*hj)/6.0);
  }
  
  public static int locateBin(double xval, double[] knots) {
    if (xval <= knots[0])  //small short cut
      return 0;
    int highIndex = knots.length-1;
    if (xval >= knots[highIndex]) // small short cut
      return (highIndex-1);

    int tryBin = -1;
    int count = 0;
    int numBins = knots.length;
    int lowIndex = 0;

    while (count < numBins) {
      tryBin = (int) Math.floor((highIndex+lowIndex)*0.5);
      if ((xval >= knots[tryBin]) && (xval < knots[tryBin+1]))
        return tryBin;
      else if (xval > knots[tryBin])
        lowIndex = tryBin;
      else if (xval < knots[tryBin])
        highIndex = tryBin;

      count++;
    }
    return tryBin;
  }

  public static void updateAFunc(double[] basisVals, double xval, int binIndex, double[] knots, double[] hj) {
    int jp1 = binIndex+1;
    basisVals[binIndex] += gen_a_m_j(knots[jp1], xval, hj[binIndex]);
    basisVals[jp1] += gen_a_p_j(knots[binIndex], xval, hj[binIndex]);
  }

  public static void updateFMatrixCFunc(double[] basisVals, double xval, int binIndex, double[] knots, double[] hj,
                                        double[][] binvD) {
    int numKnots = basisVals.length;
    int matSize = binvD.length;
    int jp1 = binIndex+1;
    double cmj = gen_c_m_j(knots[jp1], xval, hj[binIndex]);
    double cpj = gen_c_p_j(knots[binIndex], xval, hj[binIndex]);
    int binIndexM1 = binIndex-1;
    for (int index=0; index < numKnots; index++) {
      if (binIndex == 0) {  // only one part
        basisVals[index] = binvD[binIndex][index] * cpj;
      } else if (binIndex >= matSize) { // update only one part
        basisVals[index] = binvD[binIndexM1][index] * cmj;
      } else { // binIndex > 0 and binIndex < matSize
        basisVals[index] = binvD[binIndexM1][index] * cmj+binvD[binIndex][index] * cpj;
      }
    }
  }
  
  public static void expandOneGamCol(double xval, double[][] binvD, double[] basisVals, double[] hj, double[] knots) {
    if (!Double.isNaN(xval)) {
      int binIndex = locateBin(xval, knots);
      updateFMatrixCFunc(basisVals, xval, binIndex, knots, hj, binvD);
      updateAFunc(basisVals, xval, binIndex, knots, hj);
    } else {
      Arrays.fill(basisVals, Double.NaN);
    }
  }
}
