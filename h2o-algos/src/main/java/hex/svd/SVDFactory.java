package hex.svd;

/**
 * @author mathemage <ha@h2o.ai>
 * created on 2.5.17
 */
public class SVDFactory {
  public static SVDInterface createSVDImplementation(double[][] gramMatrix, SVDImplementation implementation)
      throws Exception {
    switch (implementation) {
      case EVD_MTJ_DENSEMATRIX:
        return new EVDMTJDenseMatrix(gramMatrix);
      case MTJ:
        return new SVDMTJ(gramMatrix);
      case MTJ_DENSEMATRIX:
        return new SVDMTJDenseMatrix(gramMatrix);
      case JAMA:
        return new SVDJama(gramMatrix);
      default:
        throw new Exception("Unrecognized svdImplementation " + implementation.toString());
    }
  }
}
