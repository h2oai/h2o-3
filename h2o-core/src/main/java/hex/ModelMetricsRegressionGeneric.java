package hex;

import water.fvec.Frame;

public class ModelMetricsRegressionGeneric extends ModelMetricsRegression {

  public ModelMetricsRegressionGeneric(Model model, Frame frame, long nobs, double mse, double sigma, double mae, double rmsle,
                                       double meanResidualDeviance, CustomMetric customMetric, String description) {
    super(model, frame, nobs, mse, sigma, mae, rmsle, meanResidualDeviance, customMetric);
    _description = description;
  }
}
