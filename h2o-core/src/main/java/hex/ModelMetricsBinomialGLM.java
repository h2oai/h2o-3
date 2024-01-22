package hex;

import water.fvec.Frame;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial implements GLMMetrics {
  public final long _nullDegreesOfFreedom;
  public final long _residualDegreesOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;
  public final double _loglikelihood;

  public ModelMetricsBinomialGLM(Model model, Frame frame, long nobs, double mse, String[] domain,
                                 double sigma, AUC2 auc, double logloss, double resDev, double nullDev,
                                 double aic, long nDof, long rDof, GainsLift gainsLift,
                                 CustomMetric customMetric, double loglikelihood) {
    super(model, frame, nobs, mse, domain, sigma, auc, logloss, loglikelihood, aic, gainsLift, customMetric);
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _nullDegreesOfFreedom = nDof;
    _residualDegreesOfFreedom = rDof;
    _loglikelihood = loglikelihood;
  }

  @Override
  public double residual_deviance() {return _resDev;}

  @Override
  public double null_deviance() {return _nullDev;}

  @Override
  public long residual_degrees_of_freedom(){
    return _residualDegreesOfFreedom;
  }

  @Override
  public long null_degrees_of_freedom() {return _nullDegreesOfFreedom;}

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" null DOF: " + (float) _nullDegreesOfFreedom+ "\n");
    sb.append(" residual DOF: " + (float) _residualDegreesOfFreedom+ "\n");
    sb.append(" null deviance: " + (float)_nullDev + "\n");
    sb.append(" residual deviance: " + (float)_resDev + "\n");
    return sb.toString();
  }

  @Override public boolean equals(Object o) {
    if(!(o instanceof ModelMetricsBinomialGLM))
      return false;
    ModelMetricsBinomialGLM mm = (ModelMetricsBinomialGLM)o;
    return
      _residualDegreesOfFreedom == mm._residualDegreesOfFreedom &&
      _nullDegreesOfFreedom == mm._nullDegreesOfFreedom &&
      Math.abs(_resDev - mm._resDev) < 1e-8;
  }

  public static class ModelMetricsMultinomialGLM extends ModelMetricsMultinomial implements GLMMetrics {
    public final long _nullDegreesOfFreedom;
    public final long _residualDegreesOfFreedom;
    public final double _resDev;
    public final double _nullDev;
    public final double _AIC;
    public final double _loglikelihood;

    public ModelMetricsMultinomialGLM(Model model, Frame frame, long nobs, double mse, String[] domain,
                                      double sigma, ConfusionMatrix cm, float [] hr, double logloss,
                                      double resDev, double nullDev, double aic, long nDof, long rDof,
                                      MultinomialAUC auc, CustomMetric customMetric, double loglikelihood) {
      super(model, frame, nobs,  mse, domain, sigma, cm, hr, logloss, loglikelihood, aic, auc, customMetric);
      _resDev = resDev;
      _nullDev = nullDev;
      _AIC = aic;
      _nullDegreesOfFreedom = nDof;
      _residualDegreesOfFreedom = rDof;
      _loglikelihood = loglikelihood;
    }

    @Override
    public double residual_deviance() {return _resDev;}

    @Override
    public double null_deviance() {return _nullDev;}

    @Override
    public long residual_degrees_of_freedom(){
      return _residualDegreesOfFreedom;
    }

    @Override
    public long null_degrees_of_freedom() {return _nullDegreesOfFreedom;}

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(super.toString());
      sb.append(" null DOF: " + (float) _nullDegreesOfFreedom+ "\n");
      sb.append(" residual DOF: " + (float) _residualDegreesOfFreedom+ "\n");
      sb.append(" null deviance: " + (float)_nullDev + "\n");
      sb.append(" residual deviance: " + (float)_resDev + "\n");
      return sb.toString();
    }

    @Override public boolean equals(Object o) {
      if(!(o instanceof ModelMetricsMultinomialGLM))
        return false;
      ModelMetricsMultinomialGLM mm = (ModelMetricsMultinomialGLM)o;
      return
        _residualDegreesOfFreedom == mm._residualDegreesOfFreedom &&
          _nullDegreesOfFreedom == mm._nullDegreesOfFreedom &&
          Math.abs(_resDev - mm._resDev) < 1e-8;
    }
  }

  public static class ModelMetricsOrdinalGLM extends ModelMetricsOrdinal implements GLMMetrics {
    public final long _nullDegreesOfFreedom;
    public final long _residualDegreesOfFreedom;
    public final double _resDev;
    public final double _nullDev;
    public final double _AIC;
    public final double _loglikelihood;

    public ModelMetricsOrdinalGLM(Model model, Frame frame, long nobs, double mse, String[] domain,
                                  double sigma, ConfusionMatrix cm, float [] hr, double logloss,
                                  double resDev, double nullDev, double aic, long nDof, long rDof,
                                  CustomMetric customMetric, double loglikelihood) {
      super(model, frame, nobs,  mse, domain, sigma, cm, hr, logloss, customMetric);
      _resDev = resDev;
      _nullDev = nullDev;
      _AIC = aic;
      _nullDegreesOfFreedom = nDof;
      _residualDegreesOfFreedom = rDof;
      _loglikelihood = loglikelihood;
    }

    @Override
    public double residual_deviance() {return _resDev;}

    @Override
    public double null_deviance() {return _nullDev;}

    @Override
    public long residual_degrees_of_freedom(){
      return _residualDegreesOfFreedom;
    }

    @Override
    public long null_degrees_of_freedom() {return _nullDegreesOfFreedom;}

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(super.toString());
      sb.append(" null DOF: " + (float) _nullDegreesOfFreedom+ "\n");
      sb.append(" residual DOF: " + (float) _residualDegreesOfFreedom+ "\n");
      sb.append(" null deviance: " + (float)_nullDev + "\n");
      sb.append(" residual deviance: " + (float)_resDev + "\n");
      return sb.toString();
    }

    @Override public boolean equals(Object o) {
      if(!(o instanceof ModelMetricsOrdinalGLM))
        return false;
      ModelMetricsOrdinalGLM mm = (ModelMetricsOrdinalGLM)o;
      return
              _residualDegreesOfFreedom == mm._residualDegreesOfFreedom &&
                      _nullDegreesOfFreedom == mm._nullDegreesOfFreedom &&
                      Math.abs(_resDev - mm._resDev) < 1e-8;
    }
  }

}
