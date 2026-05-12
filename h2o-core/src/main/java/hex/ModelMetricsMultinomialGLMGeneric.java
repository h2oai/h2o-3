package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsMultinomialGLMGeneric extends ModelMetricsMultinomialGeneric {

  public final long _nullDegreesOfFreedom;
  public final long _residualDegreesOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;
  public final double _loglikelihood;
  public final TwoDimTable _coefficients_table;

  public ModelMetricsMultinomialGLMGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                           TwoDimTable confusion_matrix, TwoDimTable hit_ratio_table, double logloss, CustomMetric customMetric,
                                           double mean_per_class_error, long nullDegreesOfFreedom, long residualDegreesOfFreedom,
                                           double resDev, double nullDev, TwoDimTable coefficients_table, double r2,
                                           TwoDimTable multinomial_auc_table, TwoDimTable multinomial_aucpr_table, MultinomialAucType type,
                                           final String description) {
    this(model, frame, nobs, mse,  domain, sigma, confusion_matrix, hit_ratio_table, logloss, customMetric, 
            mean_per_class_error, nullDegreesOfFreedom, residualDegreesOfFreedom, resDev, nullDev, Double.NaN, 
            coefficients_table, r2, multinomial_auc_table, multinomial_aucpr_table, type, description, Double.NaN);
  }
  public ModelMetricsMultinomialGLMGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                           TwoDimTable confusion_matrix, TwoDimTable hit_ratio_table, double logloss, CustomMetric customMetric,
                                           double mean_per_class_error, long nullDegreesOfFreedom, long residualDegreesOfFreedom,
                                           double resDev, double nullDev, double aic, TwoDimTable coefficients_table, double r2,
                                           TwoDimTable multinomial_auc_table, TwoDimTable multinomial_aucpr_table, MultinomialAucType type, 
                                           final String description, double loglikelihood) {
    super(model, frame, nobs, mse, domain, sigma, confusion_matrix, hit_ratio_table, logloss, customMetric, mean_per_class_error, r2,
            multinomial_auc_table, multinomial_aucpr_table, type, description);
    _nullDegreesOfFreedom = nullDegreesOfFreedom;
    _residualDegreesOfFreedom = residualDegreesOfFreedom;
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _coefficients_table = coefficients_table;
    _loglikelihood = loglikelihood;
  }
}
