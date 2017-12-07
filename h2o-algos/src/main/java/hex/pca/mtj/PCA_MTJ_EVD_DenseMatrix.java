package hex.pca.mtj;

import hex.pca.PCAInterface;
import hex.util.EigenPair;
import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.Matrix;
import no.uib.cipr.matrix.NotConvergedException;
import water.util.ArrayUtils;

/**
 * @author mathemage <ha@h2o.ai>
 *         created on 1.5.17
 */
public class PCA_MTJ_EVD_DenseMatrix implements PCAInterface {
  private DenseMatrix gramMatrix;
  private no.uib.cipr.matrix.EVD evd;
  private double[] eigenvalues;
  private double[][] eigenvectors;

  public PCA_MTJ_EVD_DenseMatrix(double[][] gramMatrix) {
    this.gramMatrix = new DenseMatrix(gramMatrix);
    runEVD();
  }

  private void runEVD() {
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
    EigenPair[] eigenPairs = LinearAlgebraUtils.createReverseSortedEigenpairs(eigenvalues, eigenvectors);
    eigenvalues = LinearAlgebraUtils.extractEigenvaluesFromEigenpairs(eigenPairs);
    eigenvectors = ArrayUtils.transpose(LinearAlgebraUtils.extractEigenvectorsFromEigenpairs(eigenPairs));
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
