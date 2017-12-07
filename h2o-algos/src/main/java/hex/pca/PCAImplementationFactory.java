package hex.pca;

import hex.pca.jama.PCAJama;
import hex.pca.mtj.PCA_MTJ_EVD_DenseMatrix;
import hex.pca.mtj.PCA_MTJ_EVD_SymmMatrix;
import hex.pca.mtj.PCA_MTJ_SVD_DenseMatrix;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 2.5.17
 */
class PCAImplementationFactory {
  static PCAInterface createSVDImplementation(double[][] gramMatrix, PCAImplementation implementation)
      throws Exception {
    switch (implementation) {
      case MTJ_EVD_DENSEMATRIX:
        return new PCA_MTJ_EVD_DenseMatrix(gramMatrix);
      case MTJ_EVD_SYMMMATRIX:
        return new PCA_MTJ_EVD_SymmMatrix(gramMatrix);
      case MTJ_SVD_DENSEMATRIX:
        return new PCA_MTJ_SVD_DenseMatrix(gramMatrix);
      case JAMA:
        return new PCAJama(gramMatrix);
      default:
        throw new Exception("Unrecognized svdImplementation " + implementation.toString());
    }
  }
}
