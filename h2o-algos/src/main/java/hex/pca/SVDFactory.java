package hex.pca;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 2.5.17
 */
public class SVDFactory {
  static SVDInterface createSVDbyName(double[][] gramMatrix, svdImplementation implementation)
      throws Exception {
    switch (implementation) {
      case MTJ:
        return new SVD_MTJ(gramMatrix);
      case JAMA:
        return new SVD_Jama(gramMatrix);
      default:
        throw new Exception("Unrecognized svdImplementation " + implementation.toString());
    }
  }

  enum svdImplementation {
    MTJ, JAMA
  }
}
