package water.api;

import hex.ModelMetricsClustering;

public class ModelMetricsClusteringV3 extends ModelMetricsBase<ModelMetricsClustering, ModelMetricsClusteringV3> {
  @API(help="The Within-Cluster MSE.", direction=API.Direction.OUTPUT)
  public double[] within_mse;

  @Override public ModelMetricsClustering createImpl() {
    return new ModelMetricsClustering(this.model.createImpl().get(), this.frame.createImpl().get());
  }
}
