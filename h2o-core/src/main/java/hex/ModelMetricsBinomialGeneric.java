package hex;

import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Frame;
import water.util.TwoDimTable;

public class ModelMetricsBinomialGeneric extends ModelMetricsBinomial {

  public final TwoDimTable _gainsLiftTable;
  public final TwoDimTable _thresholds_and_metric_scores;
  public final TwoDimTable _max_criteria_and_metric_scores;
  public final TwoDimTable _confusion_matrix;

  public ModelMetricsBinomialGeneric(Model model, Frame frame, long nobs, double mse, String[] domain,
                                     double sigma, AUC2 auc, double logloss, TwoDimTable gainsLiftTable,
                                     CustomMetric customMetric, double mean_per_class_error, TwoDimTable thresholds_and_metric_scores,
                                     TwoDimTable max_criteria_and_metric_scores, TwoDimTable confusion_matrix) {
    super(model, frame, nobs, mse, domain, sigma, auc, logloss, null, customMetric);
    _gainsLiftTable = gainsLiftTable;
    _thresholds_and_metric_scores = thresholds_and_metric_scores;
    _max_criteria_and_metric_scores = max_criteria_and_metric_scores;
    _confusion_matrix = confusion_matrix;
  }

  public static ModelMetricsBinomialGeneric getFromDKV(Model model, Frame frame) {
    ModelMetrics mm = ModelMetrics.getFromDKV(model, frame);
    if (!(mm instanceof ModelMetricsBinomialGeneric))
      throw new H2OIllegalArgumentException("Expected to find a Binomial ModelMetrics for model: " + model._key.toString() + " and frame: " + frame._key.toString(),
              "Expected to find a ModelMetricsBinomial for model: " + model._key.toString() + " and frame: " + frame._key.toString() + " but found a: " + (mm == null ? null : mm.getClass()));
    return (ModelMetricsBinomialGeneric) mm;
  }

}
