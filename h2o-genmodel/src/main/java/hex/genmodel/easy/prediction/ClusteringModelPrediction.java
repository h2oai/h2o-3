package hex.genmodel.easy.prediction;

/**
 * Clustering model prediction.
 */
public class ClusteringModelPrediction extends AbstractPrediction {
  /**
   * Chosen cluster for this data point.
   */
  public int cluster;

  /**
   * (Optional) Vector of squared distances to all cluster centers.
   * This field will only be included in the output if "useExtendedOutput" flag was enabled in EasyPredictModelWrapper.
   */
  public double[] distances = null;

}
