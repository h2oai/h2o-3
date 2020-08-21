package hex.gam.GamSplines;

import hex.gam.MatrixFrameUtils.TriDiagonalMatrix;
import hex.util.LinearAlgebraUtils;
import water.util.ArrayUtils;

import static hex.genmodel.utils.ArrayUtils.eleDiff;
import static hex.util.LinearAlgebraUtils.generateTriDiagMatrix;

public class CubicRegressionSplines {
  public double[] _knots;  // store knot values for the spline class
  public double[] _hj;     // store difference between knots, length _knotNum-1
  int _knotNum; // number of knot values
  
  public CubicRegressionSplines(int knotNum, double[] knots) {
    _knotNum = knotNum;
    _knots = knots;
    _hj = eleDiff(_knots);
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
}
