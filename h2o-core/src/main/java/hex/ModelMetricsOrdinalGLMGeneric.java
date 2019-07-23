package hex;

import water.fvec.Frame;
import water.util.TwoDimTable;


public class ModelMetricsOrdinalGLMGeneric extends ModelMetricsOrdinalGeneric {
  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;

  public ModelMetricsOrdinalGLMGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma, TwoDimTable confusionMatrix,
                                       float[] hr, double logloss, CustomMetric customMetric, double r2, long nullDegressOfFreedom, long residualDegressOfFreedom, double resDev, double nullDev, double aic) {
    super(model, frame, nobs, mse, domain, sigma, confusionMatrix, hr, logloss, customMetric, r2);
    _nullDegressOfFreedom = nullDegressOfFreedom;
    _residualDegressOfFreedom = residualDegressOfFreedom;
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
  }
}
