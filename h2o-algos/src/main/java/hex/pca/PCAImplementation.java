package hex.pca;

/**
 * @author mathemage <ha@h2o.ai>
 *         created on 2.5.17
 */
public enum PCAImplementation {
	MTJ_EVD_DENSEMATRIX, MTJ_EVD_SYMMMATRIX, MTJ_SVD_DENSEMATRIX, JAMA;
  final static PCAImplementation fastestImplementation = MTJ_EVD_SYMMMATRIX;    // set to the fastest implementation
  
  public static PCAImplementation getFastestImplementation() {
    return fastestImplementation;
  }
}
