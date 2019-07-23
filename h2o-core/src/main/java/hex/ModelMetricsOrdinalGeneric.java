package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsOrdinalGeneric extends ModelMetricsOrdinal {

  public final double _r2;
  public final TwoDimTable _confusion_matrix;

  public ModelMetricsOrdinalGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, TwoDimTable confusionMatrix,
                                    float[] hr, double logloss, CustomMetric customMetric, double r2) {
    super(model, frame, nobs, mse, domain, sigma, null, hr, logloss, customMetric);
    _r2 = r2;
    _confusion_matrix = confusionMatrix;
  }

  @Override
  public double r2() {
    return _r2;
  }
}
