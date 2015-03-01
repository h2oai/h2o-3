package water.api;

import hex.kmeans.ModelMetricsKMeans;

public class ModelMetricsKMeansV3 extends ModelMetricsBase<ModelMetricsKMeans, ModelMetricsKMeansV3> {
  @API(help="Cluster Size[k]")
  public long[/*k*/] size;

  @API(help="Within cluster Mean Square Error per cluster")
  public double[/*k*/] within_mse;   // Within-cluster MSE, variance

  @API(help="Average within cluster Mean Square Error")
  public double avg_within_ss;       // Average within-cluster MSE, variance

  @API(help="Average Mean Square Error to grand mean")
  public double avg_ss;    // Total MSE to grand mean centroid

  @API(help="Average between cluster Mean Square Error")
  public double avg_between_ss;
}
