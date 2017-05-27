package hex.svd;

import hex.util.EigenPair;
import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.NotConvergedException;
import water.util.ArrayUtils;

import java.util.List;

import static java.util.Collections.reverse;

/**
 * @author mathemage <ha@h2o.ai>
 *         created on 1.5.17
 */
public class EVDMTJDenseMatrix implements SVDInterface {
  private static DenseMatrix gramMatrix;
  private static no.uib.cipr.matrix.EVD evd;
  private static double[] eigenvalues;
  private static double[][] eigenvectors;

  EVDMTJDenseMatrix(double[][] gramMatrix) {
    EVDMTJDenseMatrix.gramMatrix = new DenseMatrix(gramMatrix);
    runEVD();
  }

  private static void runEVD() {
    int gramDimension = gramMatrix.numRows();
    try {
      evd = no.uib.cipr.matrix.EVD.factorize(gramMatrix);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    // initial eigenpairs
    eigenvalues = evd.getRealEigenvalues();
    Matrix eigenvectorMatrix = evd.getRightEigenvectors();
    eigenvectors = LinearAlgebraUtils.reshape1DArray(((DenseMatrix) eigenvectorMatrix).getData(), gramDimension,
        gramDimension);

    // sort eigenpairs in descending order according to the magnitude of eigenvalues
    List<EigenPair> eigenPairs = EigenPair.getSortedEigenpairs(gramDimension, eigenvalues, eigenvectors);
    reverse(eigenPairs);
    eigenvalues = EigenPair.extractEigenvalues(eigenPairs);
    eigenvectors = ArrayUtils.transpose(EigenPair.extractEigenvectors(eigenPairs));
  }

  @Override
  public double[] getVariances() {
    return eigenvalues;
  }

  @Override
  public double[][] getPrincipalComponents() {
    return eigenvectors;
  }
}
