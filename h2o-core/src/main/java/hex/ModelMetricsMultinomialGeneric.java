package hex;

import hex.genmodel.GenModel;
import water.MRTask;
import water.Scope;
import water.exceptions.H2OIllegalArgumentException;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.ArrayUtils;
import water.util.MathUtils;
import water.util.TwoDimTable;

import java.util.Arrays;

public class ModelMetricsMultinomialGeneric extends ModelMetricsMultinomial {


  public final TwoDimTable _hit_ratio_table;
  public final TwoDimTable _confusion_matrix_table;

  public ModelMetricsMultinomialGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                        TwoDimTable confusion_matrix, TwoDimTable hit_ratio_table, double logloss, CustomMetric customMetric,
                                        double mean_per_class_error) {
    super(model, frame, nobs, mse, domain, sigma, null, null, logloss, customMetric);
    _confusion_matrix_table = confusion_matrix; 
    _hit_ratio_table = hit_ratio_table;
  }


}
