package hex;

import water.fvec.Frame;

public class ModelMetricsRegressionGLMGeneric extends ModelMetricsRegressionGLM {

  public final double _r2;

  public ModelMetricsRegressionGLMGeneric(Model model, Frame frame, long nobs, double mse, double sigma, double mae, double rmsle,
                                          double meanResidualDeviance, CustomMetric customMetric, double r2, long nullDegressOfFreedom, long residualDegressOfFreedom, double resDev, double nullDev, double aic) {
    super(model, frame, nobs, mse, sigma, mae, rmsle, resDev, meanResidualDeviance, nullDev, aic, nullDegressOfFreedom, residualDegressOfFreedom, customMetric);
    _r2 = r2;
  }

  @Override
  public double r2() {
    return _r2;
  }
}
