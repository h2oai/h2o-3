package hex.pca.mtj;

import hex.pca.PCAInterface;
import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.NotConvergedException;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 1.5.17
 */
public class PCA_MTJ_SVD_DenseMatrix implements PCAInterface {
  private DenseMatrix gramMatrix;
  private no.uib.cipr.matrix.SVD svd;
  private double[][] rightEigenvectors;

  public PCA_MTJ_SVD_DenseMatrix(double[][] gramMatrix) {
    this.gramMatrix = new DenseMatrix(gramMatrix);
    runSVD();
  }

  @Override
  public double[] getVariances() {
    return svd.getS();
  }

  @Override
  public double[][] getPrincipalComponents() {
    return rightEigenvectors;
  }

  private void runSVD() {
    int gramDimension = gramMatrix.numRows();
    try {
      svd = new no.uib.cipr.matrix.SVD(gramDimension, gramDimension).factor(gramMatrix);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    double[] Vt_1D = svd.getVt().getData();
    rightEigenvectors = LinearAlgebraUtils.reshape1DArray(Vt_1D, gramDimension, gramDimension);
  }
}
