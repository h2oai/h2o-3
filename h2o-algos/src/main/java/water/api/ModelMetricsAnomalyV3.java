package water.api;

import hex.ModelMetrics;
import hex.tree.isofor.ModelMetricsAnomaly;
import water.api.schemas3.ModelMetricsBaseV3;

public class ModelMetricsAnomalyV3 extends ModelMetricsBaseV3<ModelMetricsAnomaly, ModelMetricsAnomalyV3> {

  @API(help = "Mean Anomaly Score.", direction = API.Direction.OUTPUT)
  public double mean_score;

  @API(help = "Mean Normalized Anomaly Score.", direction = API.Direction.OUTPUT)
  public double mean_normalized_score;

  @Override
  public ModelMetricsAnomalyV3 fillFromImpl(ModelMetricsAnomaly modelMetrics) {
    ModelMetricsAnomalyV3 mma = super.fillFromImpl(modelMetrics);
    return mma;
  }

}
