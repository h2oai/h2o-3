package hex;

import water.fvec.Frame;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial {
  public final double _resDev;
  public final double _nullDev;
  public final double _aic;

  public ModelMetricsBinomialGLM(Model model, Frame frame, double mse, String[] domain, double sigma, AUC2 auc, double resDev, double nullDev, double aic) {
    super(model, frame, mse, domain, sigma, auc, Double.NaN);
    _resDev = resDev;
    _nullDev = nullDev;
    _aic = aic;
  }
}
