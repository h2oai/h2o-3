package water.api.schemas3;

import hex.ModelMetricsRegressionCoxPH;
import water.api.API;
import water.api.API.Direction;

public class ModelMetricsRegressionCoxPHV3 extends ModelMetricsRegressionV3<ModelMetricsRegressionCoxPH, ModelMetricsRegressionCoxPHV3> {
  @API(help="concordance",direction = Direction.OUTPUT)
  public double concordance;

  @Override
  public ModelMetricsRegressionCoxPHV3 fillFromImpl(ModelMetricsRegressionCoxPH modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.concordance = modelMetrics.concordance();
    return this;
  }

}
