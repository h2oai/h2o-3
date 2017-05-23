package hex.svd;

/**
 * @author mathemage </ha@h2o.ai>
 * created on 2.5.17
 */
public interface SVDInterface {
  double[] getVariances();

  double[][] getPrincipalComponents();
}
