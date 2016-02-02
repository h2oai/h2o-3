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
  public final double _AIC;

  public ModelMetricsRegressionGLM(Model model, Frame frame, double mse, double sigma, double resDev, double meanResDev, double nullDev, double aic, long nDof, long rDof) {
    super(model, frame, mse, sigma, meanResDev);
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
    sb.append(" AIC: " + (float)_AIC + "\n");
    return sb.toString();
  }

}
