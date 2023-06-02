package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsOrdinalGLMGeneric extends ModelMetricsOrdinalGeneric {
  public final long _nullDegreesOfFreedom;
  public final long _residualDegreesOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;
  public final double _loglikelihood;
  public final TwoDimTable _coefficients_table;
  public final double _r2;

  public ModelMetricsOrdinalGLMGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                       TwoDimTable confusionMatrix, float[] hr, double logloss, CustomMetric customMetric, double r2, long nullDegreesOfFreedom,
                                       long residualDegreesOfFreedom, double resDev, double nullDev, double aic, double loglikelihood,
                                       TwoDimTable coefficients_table, TwoDimTable hit_ratio_table, double meanPerClassError, String description) {
    super(model, frame, nobs, mse, domain, sigma, confusionMatrix, hr, logloss, customMetric, hit_ratio_table,
            meanPerClassError, description);
    _nullDegreesOfFreedom = nullDegreesOfFreedom;
    _residualDegreesOfFreedom = residualDegreesOfFreedom;
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _loglikelihood = loglikelihood;
    _coefficients_table = coefficients_table;
    _r2 = r2;
  }
  
}
