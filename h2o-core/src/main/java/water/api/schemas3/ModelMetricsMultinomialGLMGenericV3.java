package water.api.schemas3;

import hex.ModelMetricsMultinomialGLMGeneric;
import water.api.API;
import water.api.API.Direction;

public class ModelMetricsMultinomialGLMGenericV3 extends ModelMetricsMultinomialV3<ModelMetricsMultinomialGLMGeneric, ModelMetricsMultinomialGLMGenericV3> {
  @API(help="residual deviance",direction = Direction.OUTPUT)
  public double residual_deviance;

  @API(help="null deviance",direction = Direction.OUTPUT)
  public double null_deviance;

  @API(help="AIC",direction = Direction.OUTPUT)
  public double AIC;

  @API(help="null DOF", direction= Direction.OUTPUT)
  public long null_degrees_of_freedom;

  @API(help="residual DOF", direction= Direction.OUTPUT)
  public long residual_degrees_of_freedom;

  @Override
  public ModelMetricsMultinomialGLMGenericV3 fillFromImpl(ModelMetricsMultinomialGLMGeneric mms) {
    super.fillFromImpl(mms);
    this.AIC = mms._AIC;
    this.residual_deviance = mms._resDev;
    this.null_deviance = mms._nullDev;
    this.null_degrees_of_freedom = mms._nullDegressOfFreedom;
    this.residual_degrees_of_freedom = mms._residualDegressOfFreedom;
    return this;
  }

}
