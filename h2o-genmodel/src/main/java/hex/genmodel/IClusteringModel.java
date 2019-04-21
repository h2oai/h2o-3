package hex.genmodel;

/**
 * Clustering Model Interface
 */
public interface IClusteringModel {

  double[] score0(double[] row, double[] preds);

  /**
   * Calculates squared distances to all cluster centers.
   *
   * {@see hex.genmodel.GenModel.KMeans_distances(..)} for precise definition
   * of the distance metric.
   *
   * Pass in data in a double[], in a same way as to the score0 function.
   * Cluster distances will be stored into the distances[] array. Function
   * will return the closest cluster. This way the caller can avoid to call
   * score0(..) to retrieve the cluster where the data point belongs.
   *
   * Warning: This function can modify content of row array (same as for score0).
   *
   * @param row input row
   * @param distances vector of distances
   * @return index of closest cluster
   */
  int distances(double[] row, double[] distances);

  /**
   * Returns number of cluster used by this model.
   * @return number of clusters
   */
  int getNumClusters();

}
