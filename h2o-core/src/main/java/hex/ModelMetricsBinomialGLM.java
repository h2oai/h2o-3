package hex;

import water.fvec.Frame;
import water.util.ComparisonUtils;

public class ModelMetricsBinomialGLM extends ModelMetricsBinomial implements GLMMetrics {
  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;

  public ModelMetricsBinomialGLM(Model model, Frame frame, long nobs, double mse, String[] domain,
                                 double sigma, AUC2 auc, double logloss, double resDev, double nullDev,
                                 double aic, long nDof, long rDof, GainsLift gainsLift,
                                 CustomMetric customMetric) {
    super(model, frame, nobs, mse, domain, sigma, auc, logloss, gainsLift, customMetric);
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _nullDegressOfFreedom = nDof;
    _residualDegressOfFreedom = rDof;
  }

  @Override
  public double residual_deviance() {return _resDev;}

  @Override
  public double null_deviance() {return _nullDev;}

  @Override
  public long residual_degrees_of_freedom(){
    return _residualDegressOfFreedom;
  }

  @Override
  public long null_degrees_of_freedom() {return _nullDegressOfFreedom;}

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

  @Override
  public boolean isEqualUpToTolerance(ComparisonUtils.MetricComparator comparator, ModelMetrics other) {
    super.isEqualUpToTolerance(comparator, other);
    ModelMetricsBinomialGLM specificOther = (ModelMetricsBinomialGLM) other;
    
    GLMMetrics.compareMetricsUpToTolerance(comparator, this, specificOther);
    
    return comparator.isEqual();
  }
  
  public static class ModelMetricsMultinomialGLM extends ModelMetricsMultinomial implements GLMMetrics {
    public final long _nullDegressOfFreedom;
    public final long _residualDegressOfFreedom;
    public final double _resDev;
    public final double _nullDev;
    public final double _AIC;

    public ModelMetricsMultinomialGLM(Model model, Frame frame, long nobs, double mse, String[] domain,
                                      double sigma, ConfusionMatrix cm, float [] hr, double logloss,
                                      double resDev, double nullDev, double aic, long nDof, long rDof,
                                      MultinomialAUC auc, CustomMetric customMetric) {
      super(model, frame, nobs,  mse, domain, sigma, cm, hr, logloss, auc, customMetric);
      _resDev = resDev;
      _nullDev = nullDev;
      _AIC = aic;
      _nullDegressOfFreedom = nDof;
      _residualDegressOfFreedom = rDof;
    }

    @Override
    public double residual_deviance() {return _resDev;}

    @Override
    public double null_deviance() {return _nullDev;}

    @Override
    public long residual_degrees_of_freedom(){
      return _residualDegressOfFreedom;
    }

    @Override
    public long null_degrees_of_freedom() {return _nullDegressOfFreedom;}

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

    @Override
    public boolean isEqualUpToTolerance(ComparisonUtils.MetricComparator comparator, ModelMetrics other) {
      super.isEqualUpToTolerance(comparator, other);
      ModelMetricsMultinomialGLM specificOther = (ModelMetricsMultinomialGLM) other;
      
      GLMMetrics.compareMetricsUpToTolerance(comparator, this, specificOther);
      
      return comparator.isEqual();
    }
  }

  public static class ModelMetricsOrdinalGLM extends ModelMetricsOrdinal implements GLMMetrics {
    public final long _nullDegressOfFreedom;
    public final long _residualDegressOfFreedom;
    public final double _resDev;
    public final double _nullDev;
    public final double _AIC;

    public ModelMetricsOrdinalGLM(Model model, Frame frame, long nobs, double mse, String[] domain,
                                      double sigma, ConfusionMatrix cm, float [] hr, double logloss,
                                      double resDev, double nullDev, double aic, long nDof, long rDof,
                                      CustomMetric customMetric) {
      super(model, frame, nobs,  mse, domain, sigma, cm, hr, logloss, customMetric);
      _resDev = resDev;
      _nullDev = nullDev;
      _AIC = aic;
      _nullDegressOfFreedom = nDof;
      _residualDegressOfFreedom = rDof;
    }

    @Override
    public double residual_deviance() {return _resDev;}

    @Override
    public double null_deviance() {return _nullDev;}

    @Override
    public long residual_degrees_of_freedom(){
      return _residualDegressOfFreedom;
    }

    @Override
    public long null_degrees_of_freedom() {return _nullDegressOfFreedom;}

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
      if(!(o instanceof ModelMetricsOrdinalGLM))
        return false;
      ModelMetricsOrdinalGLM mm = (ModelMetricsOrdinalGLM)o;
      return
              _residualDegressOfFreedom == mm._residualDegressOfFreedom &&
                      _nullDegressOfFreedom     == mm._nullDegressOfFreedom     &&
                      Math.abs(_resDev - mm._resDev) < 1e-8;
    }

    @Override
    public boolean isEqualUpToTolerance(ComparisonUtils.MetricComparator comparator, ModelMetrics other) {
      super.isEqualUpToTolerance(comparator, other);
      ModelMetricsOrdinalGLM specificOther = (ModelMetricsOrdinalGLM) other;
      
      GLMMetrics.compareMetricsUpToTolerance(comparator, this, specificOther);
      
      return comparator.isEqual();
    }
  }
}
