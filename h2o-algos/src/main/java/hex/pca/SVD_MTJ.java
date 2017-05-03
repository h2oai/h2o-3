package hex.pca;

import hex.util.LinearAlgebraUtils;
import no.uib.cipr.matrix.DenseMatrix;
import no.uib.cipr.matrix.NotConvergedException;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 1.5.17
 */
public class SVD_MTJ implements SVDInterface {
  private DenseMatrix gramMatrix;
  private no.uib.cipr.matrix.SVD svd;
  private double[][] rightEigenvectors;

  SVD_MTJ(double[][] gramMatrix) {
    this.gramMatrix = new DenseMatrix(gramMatrix);
    runSVD();
  }

  @Override
  public double[] getSingularValues() {
    return svd.getS();
  }

  @Override
  public double[][] getRightEigenvectors() {
    return rightEigenvectors;
  }

  private void runSVD() {
    int gramDimension = gramMatrix.numRows();
    try {
      // Note: gramMatrix will be overwritten after this
      svd = new no.uib.cipr.matrix.SVD(gramDimension, gramDimension).factor(gramMatrix);
    } catch (NotConvergedException e) {
      throw new RuntimeException(e);
    }
    double[] Vt_1D = svd.getVt().getData();
    rightEigenvectors = LinearAlgebraUtils.reshape1DArray(Vt_1D, gramDimension, gramDimension);
  }
}
