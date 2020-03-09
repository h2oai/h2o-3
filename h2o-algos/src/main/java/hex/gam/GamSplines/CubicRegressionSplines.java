package hex.gam.GamSplines;

import hex.gam.MatrixFrameUtils.TriDiagonalMatrix;
import hex.util.LinearAlgebraUtils;
import water.util.ArrayUtils;

import static hex.util.LinearAlgebraUtils.generateTriDiagMatrix;

public class CubicRegressionSplines {
  public double[] _knots;  // store knot values for the spline class
  public double[] _hj;     // store difference between knots, length _knotNum-1
  int _knotNum; // number of knot values

/*  public CubicRegressionSplines(int knotNum, double[] knots, double vmax, double vmin) {
    _knotNum = knotNum;
    _knots = knots;
    if (knots==null) {
      _knots = MemoryManager.malloc8d(_knotNum);
      int lastKnotInd = _knotNum-1;
      double incre = (vmax-vmin)/(lastKnotInd);
      _knots[0] = vmin;
      _knots[lastKnotInd] = vmax;
      for (int index=1; index < lastKnotInd; index++) {
        _knots[index] = _knots[index-1]+incre;
      }
    }
    _hj = ArrayUtils.eleDiff(_knots);
  }*/

  public CubicRegressionSplines(int knotNum, double[] knots) {
    _knotNum = knotNum;
    _knots = knots;
    _hj = ArrayUtils.eleDiff(_knots);
  }

  public double[][] gen_BIndvD(double[] hj) {  // generate matrix bInvD
    TriDiagonalMatrix matrixD = new TriDiagonalMatrix(hj); // of dimension (_knotNum-2) by _knotNum
    double[][] matB = generateTriDiagMatrix(hj);
    // obtain cholesky of matB
    LinearAlgebraUtils.choleskySymDiagMat(matB); // verified
    // expand matB from being a lower diagonal matrix only to a full blown square matrix
    double[][] fullmatB = LinearAlgebraUtils.expandLowTrian2Ful(matB);
    // obtain inverse of matB
    double[][] bInve = LinearAlgebraUtils.chol2Inv(fullmatB, false); // verified with small matrix
    // perform inverse(matB)*matD and return it
    return LinearAlgebraUtils.matrixMultiplyTriagonal(bInve, matrixD, true);
  }

  public double[][] gen_penalty_matrix(double[] hj, double[][] binvD) {
    TriDiagonalMatrix matrixD = new TriDiagonalMatrix(hj); // of dimension (_knotNum-2) by _knotNum
    return LinearAlgebraUtils.matrixMultiplyTriagonal(ArrayUtils.transpose(binvD), matrixD, false);
  }

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
}
