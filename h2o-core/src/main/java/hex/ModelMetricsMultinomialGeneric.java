package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsMultinomialGeneric extends ModelMetricsMultinomial {
  
  public final TwoDimTable _hit_ratio_table;
  public final TwoDimTable _confusion_matrix_table;
  public final double _r2;

  public ModelMetricsMultinomialGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                        TwoDimTable confusion_matrix, TwoDimTable hit_ratio_table, double logloss, CustomMetric customMetric,
                                        double mean_per_class_error, double r2, PairwiseAUC[] aucs, final String description) {
    super(model, frame, nobs, mse, domain, sigma, null, null, logloss, aucs, customMetric);
    _confusion_matrix_table = confusion_matrix; 
    _hit_ratio_table = hit_ratio_table;
    _mean_per_class_error = mean_per_class_error;
    _r2 = r2;
     _description = description;
  }

  @Override
  public double r2() {
    return _r2;
  }
}
