package hex.pca;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 2.5.17
 */
public class SVDFactory {
  static SVDInterface createSVDbyName(PCA pca, double[][] gramMatrix, svdImplementation implementation)
      throws Exception {
    switch (implementation) {
      case SVD_MTJ:
        return new SVD_MTJ(pca, gramMatrix);
      default:
//      TODO change to:
//        throw new EnumConstantNotPresentException(implementation, implementation.toString());
        throw new Exception("Unrecognized svdImplementation " + implementation.toString());
    }
  }

  enum svdImplementation {
    SVD_MTJ
  }
}
