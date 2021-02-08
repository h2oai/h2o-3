package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;

public class ModelMetricsBinomialGeneric extends ModelMetricsBinomial {

  public final TwoDimTable _gainsLiftTable;
  public final TwoDimTable _thresholds_and_metric_scores;
  public final TwoDimTable _max_criteria_and_metric_scores;
  public final TwoDimTable _confusion_matrix;
  public final double _r2;

  public ModelMetricsBinomialGeneric(Model model, Frame frame, long nobs, double mse, String[] domain,
                                     double sigma, AUC2 auc, double logloss, TwoDimTable gainsLiftTable,
                                     CustomMetric customMetric, double mean_per_class_error, TwoDimTable thresholds_and_metric_scores,
                                     TwoDimTable max_criteria_and_metric_scores, TwoDimTable confusion_matrix, double r2,
                                     final String description) {
    super(model, frame, nobs, mse, domain, sigma, auc, logloss, null, null, customMetric);
    _gainsLiftTable = gainsLiftTable;
    // TODO implement table uplift table here
    _thresholds_and_metric_scores = thresholds_and_metric_scores;
    _max_criteria_and_metric_scores = max_criteria_and_metric_scores;
    _confusion_matrix = confusion_matrix;
    _mean_per_class_error = mean_per_class_error;
    _r2 = r2;
     _description = description;
  }

  @Override
  public double r2() {
    return _r2;
  }
  
}
