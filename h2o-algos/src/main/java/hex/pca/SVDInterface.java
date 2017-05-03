package hex.pca;

/**
 * @author mathemage </ha@h2o.ai>
 * @date 2.5.17
 */
public interface SVDInterface {
  double[] getSingularValues();

  double[][] getRightEigenvectors();
}
