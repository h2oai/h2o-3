package hex.pca;

/**
 * @author mathemage <ha@h2o.ai>
 *         created on 2.5.17
 */
public enum PCAImplementation {
  EVD_MTJ_DENSEMATRIX, EVD_MTJ_SYMM, MTJ, JAMA;
  final static PCAImplementation fastestImplementation = EVD_MTJ_SYMM;    // set to the fastest implementation
  
  public static PCAImplementation getFastestImplementation() {
    return fastestImplementation;
  }
}
