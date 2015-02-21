package water.api;

import hex.Model;
import hex.ModelMetricsClustering;
import water.fvec.Frame;

public class ModelMetricsClusteringV3 extends ModelMetricsBase<ModelMetricsClustering, ModelMetricsClusteringV3> {
  @API(help="The Total MSE.", direction=API.Direction.OUTPUT)
  public double mse;

  @API(help="The Within-Cluster MSE.", direction=API.Direction.OUTPUT)
  public double[] within_mse;

  @Override public ModelMetricsClustering createImpl() {
    return new ModelMetricsClustering(this.model.key().get(), this.frame.key().get());
  }
}
