package hex;

import water.fvec.Frame;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial {
  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;

  public ModelMetricsBinomialGLM(Model model, Frame frame, double mse, String[] domain, double sigma, AUC2 auc, double logloss, double resDev, double nullDev, double aic, long nDof, long rDof) {
    super(model, frame, mse, domain, sigma, auc, logloss);
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _nullDegressOfFreedom = nDof;
    _residualDegressOfFreedom = rDof;
  }
}
