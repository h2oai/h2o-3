package hex.genmodel;

/**
 * MOJO Models that can calculate SHAP Values (contributions) should implement this interface
 */
public interface PredictContributionsFactory {

  /**
   * Create an instance of PredictContributions
   * The returned implementation is not guaranteed to be thread-safe and the caller is responsible for making sure
   * each thread will have own copy of the instance
   * @return instance of PredictContributions
   */
  PredictContributions makeContributionsPredictor();

}
