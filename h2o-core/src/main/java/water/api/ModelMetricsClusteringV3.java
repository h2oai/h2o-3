package water.api;

import hex.ModelMetricsClustering;

public class ModelMetricsClusteringV3 extends ModelMetricsBase<ModelMetricsClustering, ModelMetricsClusteringV3> {
  @Override public ModelMetricsClustering createImpl() {
    ModelMetricsClustering m = new ModelMetricsClustering(this.model.createImpl().get(), this.frame.createImpl().get());
    return (ModelMetricsClustering) m;
  }
}
