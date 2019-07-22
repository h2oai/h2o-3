package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsMultinomialGLMGeneric extends ModelMetricsMultinomialGeneric {

  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;

  public ModelMetricsMultinomialGLMGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                           TwoDimTable confusion_matrix, TwoDimTable hit_ratio_table, double logloss, CustomMetric customMetric,
                                           double mean_per_class_error, long nullDegressOfFreedom, long residualDegressOfFreedom,
                                           double resDev, double nullDev, double aic) {
    super(model, frame, nobs, mse, domain, sigma, confusion_matrix, hit_ratio_table, logloss, customMetric, mean_per_class_error);
    _nullDegressOfFreedom = nullDegressOfFreedom;
    _residualDegressOfFreedom = residualDegressOfFreedom;
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
  }


}
