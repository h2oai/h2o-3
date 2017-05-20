package hex.pca;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 2.5.17
 */
public class SVDFactory {
  static SVDInterface createSVDImplementation(double[][] gramMatrix, SVDImplementation implementation)
      throws Exception {
    switch (implementation) {
      case EVD_MTJ_DENSEMATRIX:
        return new EVD_MTJ_DenseMatrix(gramMatrix);
      case MTJ:
        return new SVD_MTJ(gramMatrix);
      case MTJ_DENSEMATRIX:
        return new SVD_MTJ_DenseMatrix(gramMatrix);
      case JAMA:
        return new SVD_Jama(gramMatrix);
      default:
        throw new Exception("Unrecognized svdImplementation " + implementation.toString());
    }
  }
}
