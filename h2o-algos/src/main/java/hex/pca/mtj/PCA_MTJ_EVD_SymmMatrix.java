package hex.pca.mtj;

import hex.pca.PCAInterface;
import hex.util.EigenPair;
import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.NotConvergedException;
import no.uib.cipr.matrix.UpperSymmDenseMatrix;
import water.util.ArrayUtils;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 1.5.17
 */
public class PCA_MTJ_EVD_SymmMatrix implements PCAInterface {
  private UpperSymmDenseMatrix symmGramMatrix;
  private no.uib.cipr.matrix.SymmDenseEVD symmDenseEVD;
  private double[][] eigenvectors;
  private double[] eigenvalues;

  public PCA_MTJ_EVD_SymmMatrix(double[][] gramMatrix) {
    this.symmGramMatrix = new UpperSymmDenseMatrix(new DenseMatrix(gramMatrix));
    runEVD();
  }

  @Override
  public double[] getVariances() {
    return eigenvalues;
  }

  @Override
  public double[][] getPrincipalComponents() {
    return eigenvectors;
  }

  private void runEVD() {
    int gramDimension = symmGramMatrix.numRows();
    try {
      symmDenseEVD = no.uib.cipr.matrix.SymmDenseEVD.factorize(this.symmGramMatrix);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    // initial eigenpairs
    eigenvalues = symmDenseEVD.getEigenvalues();
    double[] Vt_1D = symmDenseEVD.getEigenvectors().getData();
    eigenvectors = LinearAlgebraUtils.reshape1DArray(Vt_1D, gramDimension, gramDimension);

    // sort eigenpairs in descending order according to the magnitude of eigenvalues
    EigenPair[] eigenPairs = LinearAlgebraUtils.createReverseSortedEigenpairs(eigenvalues, eigenvectors);
    eigenvalues = LinearAlgebraUtils.extractEigenvaluesFromEigenpairs(eigenPairs);
    eigenvectors = ArrayUtils.transpose(LinearAlgebraUtils.extractEigenvectorsFromEigenpairs(eigenPairs));
  }
}
