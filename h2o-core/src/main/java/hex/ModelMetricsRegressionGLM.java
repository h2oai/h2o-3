package hex;

import water.fvec.Frame;

/**
 * Created by tomasnykodym on 4/20/15.
 */
public class ModelMetricsRegressionGLM extends ModelMetricsRegression implements GLMMetrics {
  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;


  public ModelMetricsRegressionGLM(Model model, Frame frame, long nobs, double mse, double sigma,
                                   double mae, double rmsle, double resDev, double meanResDev,
                                   double nullDev, double aic, long nDof, long rDof,
                                   CustomMetric customMetric, double loglikelihood) {
    super(model, frame, nobs, mse, sigma, mae, rmsle, meanResDev, customMetric, loglikelihood, aic);
    _resDev = resDev;
    _nullDev = nullDev;
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
    sb.append(" AIC: " + (float)_AIC + "\n");
    return sb.toString();
  }

}
