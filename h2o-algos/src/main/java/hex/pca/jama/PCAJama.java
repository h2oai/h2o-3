package hex.pca.jama;

import Jama.Matrix;
import Jama.SingularValueDecomposition;
import hex.pca.PCAInterface;

public class PCAJama implements PCAInterface {
  private Matrix gramMatrix;
  private SingularValueDecomposition svd;
  private double[][] rightEigenvectors;

  public PCAJama(double[][] gramMatrix) {
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
