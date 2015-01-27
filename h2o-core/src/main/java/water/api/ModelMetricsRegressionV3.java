package water.api;

import hex.ModelMetricsRegression;

public class ModelMetricsRegressionV3 extends ModelMetricsBase<ModelMetricsRegression, ModelMetricsRegressionV3> {
  @Override public ModelMetricsRegression createImpl() {
    ModelMetricsRegression m = new ModelMetricsRegression(this.model.createImpl().get(), this.frame.createImpl().get());
    return (ModelMetricsRegression) m;
  }
}
