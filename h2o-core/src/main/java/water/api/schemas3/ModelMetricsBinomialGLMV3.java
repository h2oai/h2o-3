package water.api.schemas3;

import hex.ModelMetricsBinomialGLM;
import water.api.API;
import water.api.API.Direction;

public class ModelMetricsBinomialGLMV3 extends ModelMetricsBinomialV3<ModelMetricsBinomialGLM, ModelMetricsBinomialGLMV3> {
  @API(help="residual deviance",direction = Direction.OUTPUT)
  public double residual_deviance;

  @API(help="null deviance",direction = Direction.OUTPUT)
  public double null_deviance;

  @API(help="AIC",direction = Direction.OUTPUT)
  public double AIC;

  @API(help="log likelihood",direction = Direction.OUTPUT)
  public double loglikelihood;

  @API(help="null DOF", direction= Direction.OUTPUT)
  public long null_degrees_of_freedom;

  @API(help="residual DOF", direction= Direction.OUTPUT)
  public long residual_degrees_of_freedom;


  @Override
  public ModelMetricsBinomialGLMV3 fillFromImpl(ModelMetricsBinomialGLM modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.AIC = modelMetrics._AIC;
    this.loglikelihood = modelMetrics._loglikelihood;
    this.residual_deviance = modelMetrics._resDev;
    this.null_deviance = modelMetrics._nullDev;
    this.null_degrees_of_freedom = modelMetrics._nullDegreesOfFreedom;
    this.residual_degrees_of_freedom = modelMetrics._residualDegreesOfFreedom;
    return this;
  }

}
