package hex.pca;

/**
 * @author mathemage </ha@h2o.ai>
 * created on 2.5.17
 */
public interface PCAInterface {
  double[] getVariances();

  double[][] getPrincipalComponents();
}
