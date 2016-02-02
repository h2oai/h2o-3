package hex;

import water.fvec.Frame;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial implements GLMMetrics {
  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;

  public ModelMetricsBinomialGLM(Model model, Frame frame, double mse, String[] domain, double sigma, AUC2 auc, double logloss, double resDev, double nullDev, double aic, long nDof, long rDof, GainsLift gainsLift) {
    super(model, frame, mse, domain, sigma, auc, logloss, gainsLift);
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _nullDegressOfFreedom = nDof;
    _residualDegressOfFreedom = rDof;
  }

  @Override
  public double residualDeviance() {return _resDev;}

  @Override
  public double nullDeviance() {return _nullDev;}

  @Override
  public long residualDegreesOfFreedom(){
    return _residualDegressOfFreedom;
  }

  @Override
  public long nullDegreesOfFreedom() {return _nullDegressOfFreedom;}

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" null DOF: " + (float)_nullDegressOfFreedom + "\n");
    sb.append(" residual DOF: " + (float)_residualDegressOfFreedom + "\n");
    sb.append(" null deviance: " + (float)_nullDev + "\n");
    sb.append(" residual deviance: " + (float)_resDev + "\n");
    return sb.toString();
  }

  @Override public boolean equals(Object o) {
    if(!(o instanceof ModelMetricsBinomialGLM))
      return false;
    ModelMetricsBinomialGLM mm = (ModelMetricsBinomialGLM)o;
    return
      _residualDegressOfFreedom == mm._residualDegressOfFreedom &&
      _nullDegressOfFreedom     == mm._nullDegressOfFreedom     &&
      Math.abs(_resDev - mm._resDev) < 1e-8;
  }

  public static class ModelMetricsMultinomialGLM extends ModelMetricsMultinomial implements GLMMetrics {
    public final long _nullDegressOfFreedom;
    public final long _residualDegressOfFreedom;
    public final double _resDev;
    public final double _nullDev;
    public final double _AIC;

    public ModelMetricsMultinomialGLM(Model model, Frame frame, double mse, String[] domain, double sigma, ConfusionMatrix cm, float [] hr, double logloss, double resDev, double nullDev, double aic, long nDof, long rDof) {
      super(model, frame, mse, domain, sigma, cm, hr, logloss);
      _resDev = resDev;
      _nullDev = nullDev;
      _AIC = aic;
      _nullDegressOfFreedom = nDof;
      _residualDegressOfFreedom = rDof;
    }

    @Override
    public double residualDeviance() {return _resDev;}

    @Override
    public double nullDeviance() {return _nullDev;}

    @Override
    public long residualDegreesOfFreedom(){
      return _residualDegressOfFreedom;
    }

    @Override
    public long nullDegreesOfFreedom() {return _nullDegressOfFreedom;}

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(super.toString());
      sb.append(" null DOF: " + (float)_nullDegressOfFreedom + "\n");
      sb.append(" residual DOF: " + (float)_residualDegressOfFreedom + "\n");
      sb.append(" null deviance: " + (float)_nullDev + "\n");
      sb.append(" residual deviance: " + (float)_resDev + "\n");
      return sb.toString();
    }

    @Override public boolean equals(Object o) {
      if(!(o instanceof ModelMetricsMultinomialGLM))
        return false;
      ModelMetricsMultinomialGLM mm = (ModelMetricsMultinomialGLM)o;
      return
        _residualDegressOfFreedom == mm._residualDegressOfFreedom &&
          _nullDegressOfFreedom     == mm._nullDegressOfFreedom     &&
          Math.abs(_resDev - mm._resDev) < 1e-8;
    }
  }



}
