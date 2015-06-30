package water.api;

import hex.ModelMetricsRegression;

public class ModelMetricsRegressionV3<I extends ModelMetricsRegression, S extends ModelMetricsRegressionV3<I, S>> extends ModelMetricsBase<I, S> {
  @API(help="The R^2 for this scoring run.", direction=API.Direction.OUTPUT)
  public double r2;

//  @API(help="The Standard Deviation of the response for this scoring run.", direction=API.Direction.OUTPUT)
//  public double sigma;

  @Override public S fillFromImpl(ModelMetricsRegression modelMetrics) {
    super.fillFromImpl(modelMetrics);
    r2 = modelMetrics.r2();
    return (S) this;
  }
}
