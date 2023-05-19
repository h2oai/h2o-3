package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;

public class ModelMetricsRegressionGLMGeneric extends ModelMetricsRegressionGLM {

  public final double _r2;
  public TwoDimTable _coefficients_table;

  public ModelMetricsRegressionGLMGeneric(Model model, Frame frame, long nobs, double mse, double sigma, double mae, double rmsle,
                                          double meanResidualDeviance, CustomMetric customMetric, double r2, long nullDegreesOfFreedom,
                                          long residualDegreesOfFreedom, double resDev, double nullDev, double aic, double loglikelihood, 
                                          TwoDimTable coefficients_table) {
    super(model, frame, nobs, mse, sigma, mae, rmsle, resDev, meanResidualDeviance, nullDev, aic, nullDegreesOfFreedom, residualDegreesOfFreedom, customMetric, loglikelihood);
    _r2 = r2;
    _coefficients_table = coefficients_table; 
  }

  @Override
  public double r2() {
    return _r2;
  }
}
