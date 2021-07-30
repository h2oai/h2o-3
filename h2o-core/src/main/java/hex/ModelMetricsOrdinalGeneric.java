package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsOrdinalGeneric extends ModelMetricsOrdinal {

  public final TwoDimTable _confusion_matrix;
  public final TwoDimTable _hit_ratio_table;
  public final double _mean_per_class_error;

  public ModelMetricsOrdinalGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, TwoDimTable confusionMatrix,
                                    float[] hr, double logloss, CustomMetric customMetric, TwoDimTable hit_ratio_table,
                                    double meanPerClassError, String description) {
    super(model, frame, nobs, mse, domain, sigma, null, hr, logloss, customMetric);
    _confusion_matrix = confusionMatrix;
    _hit_ratio_table = hit_ratio_table;
    _description = description;
    _mean_per_class_error = meanPerClassError;
  }
}
