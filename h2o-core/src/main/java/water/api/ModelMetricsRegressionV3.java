package water.api;

import hex.ModelMetricsRegression;

public class ModelMetricsRegressionV3 extends ModelMetricsBase<ModelMetricsRegression, ModelMetricsRegressionV3> {
  @API(help="The Standard Deviation of the response for this scoring run.", direction=API.Direction.OUTPUT)
  public double sigma;

  @Override public ModelMetricsRegressionV3 fillFromImpl(ModelMetricsRegression modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.sigma = modelMetrics._sigma;
    return this;
  }
}
