package hex.pca;

import Jama.Matrix;
import Jama.SingularValueDecomposition;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 1.5.17
 */
public class SVD_Jama implements SVDInterface {
  private Matrix gramMatrix;
  private SingularValueDecomposition svd;
  private double[][] rightEigenvectors;

  SVD_Jama(double[][] gramMatrix) {
    this.gramMatrix = new Matrix(gramMatrix);
    runSVD();
  }

  @Override
  public double[] getVariances() {
    return svd.getSingularValues();
  }

  @Override
  public double[][] getPrincipalComponents() {
    return rightEigenvectors;
  }

  private void runSVD() {
    svd = gramMatrix.svd();
    rightEigenvectors = svd.getV().getArray();
  }
}
