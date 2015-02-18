package hex;

import water.fvec.Frame;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial {
  public final double _resDev;
  public final double _nullDev;
  public final double _aic;

  public ModelMetricsBinomialGLM(Model model, Frame frame, AUCData aucdata, double sigma, double mse, double resDev, double nullDev, double aic) {
    super(model, frame, aucdata, sigma, mse);
    _resDev = resDev;
    _nullDev = nullDev;
    _aic = aic;
  }
}
