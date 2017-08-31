package hex.pca;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 2.5.17
 */
class PCAImplementationFactory {
  static PCAInterface createSVDImplementation(double[][] gramMatrix, PCAImplementation implementation)
      throws Exception {
    switch (implementation) {
      case EVD_MTJ_DENSEMATRIX:
        return new PCA_MTJ_EVD_DenseMatrix(gramMatrix);
      case EVD_MTJ_SYMM:
        return new EVDMTJSymm(gramMatrix);
      case MTJ:
        return new SVDMTJDenseMatrix(gramMatrix);
      case JAMA:
        return new PCAJama(gramMatrix);
      default:
        throw new Exception("Unrecognized svdImplementation " + implementation.toString());
    }
  }
}
