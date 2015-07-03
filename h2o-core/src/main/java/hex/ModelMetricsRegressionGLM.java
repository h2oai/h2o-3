package hex;

import water.fvec.Frame;

/**
 * Created by tomasnykodym on 4/20/15.
 */
public class ModelMetricsRegressionGLM extends ModelMetricsRegression {
  public final long _nullDegressOfFreedom;
  public final long _residualDegressOfFreedom;
  public final double _resDev;
  public final double _nullDev;
  public final double _AIC;

  public ModelMetricsRegressionGLM(Model model, Frame frame, double mse, double sigma, double resDev, double nullDev, double aic, long nDof, long rDof) {
    super(model, frame, mse, sigma);
    _resDev = resDev;
    _nullDev = nullDev;
    _AIC = aic;
    _nullDegressOfFreedom = nDof;
    _residualDegressOfFreedom = rDof;
  }

  public String toString(){
    return "DOF = " + _nullDegressOfFreedom + " : " + _residualDegressOfFreedom + ", dev = " + _nullDev + " : " + _resDev + ", " + super.toString();
  }

}
