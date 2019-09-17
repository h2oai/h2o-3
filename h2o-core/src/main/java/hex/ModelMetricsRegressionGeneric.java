package hex;

import water.fvec.Frame;

public class ModelMetricsRegressionGeneric extends ModelMetricsRegression {
  public final double _r2;

  public ModelMetricsRegressionGeneric(Model model, Frame frame, long nobs, double mse, double sigma, double mae, double rmsle, double meanResidualDeviance, CustomMetric customMetric, double r2) {
    super(model, frame, nobs, mse, sigma, mae, rmsle, meanResidualDeviance, customMetric);
    _r2 = r2;
  }

  /**
   * For generic models, the exact R2 value is serialized with the model and provided directly, thus not calculated.
   */
  @Override
  public double r2() {
    return _r2;
  }
}
