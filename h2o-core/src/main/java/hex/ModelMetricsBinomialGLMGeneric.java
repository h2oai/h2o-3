package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;

public class ModelMetricsBinomialGLMGeneric extends ModelMetricsBinomialGeneric {

  public final long _nullDegreesOfFreedom;
  public final long _residualDegreesOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;
  public final double _loglikelihood;
  public final TwoDimTable _coefficients_table;

  public ModelMetricsBinomialGLMGeneric(Model model, Frame frame, long nobs, double mse, String[] domain,
                                        double sigma, AUC2 auc, double logloss, TwoDimTable gainsLiftTable,
                                        CustomMetric customMetric, double mean_per_class_error, TwoDimTable thresholds_and_metric_scores,
                                        TwoDimTable max_criteria_and_metric_scores, TwoDimTable confusion_matrix,
                                        long nullDegreesOfFreedom, long residualDegreesOfFreedom, double resDev, double nullDev,
                                        double aic, TwoDimTable coefficients_table, double r2, String description, double loglikelihood) {
    super(model, frame, nobs, mse, domain, sigma, auc, logloss, gainsLiftTable, customMetric, mean_per_class_error,
            thresholds_and_metric_scores, max_criteria_and_metric_scores, confusion_matrix, r2, description);
    _nullDegreesOfFreedom = nullDegreesOfFreedom;
    _residualDegreesOfFreedom = residualDegreesOfFreedom;
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _coefficients_table = coefficients_table;
    _loglikelihood = loglikelihood;
  }
}
