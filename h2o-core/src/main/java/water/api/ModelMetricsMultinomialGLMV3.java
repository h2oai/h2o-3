package water.api;

import hex.ModelMetricsBinomialGLM.ModelMetricsMultinomialGLM;
import hex.ModelMetricsMultinomial;
import water.api.API.Direction;

public class ModelMetricsMultinomialGLMV3 extends ModelMetricsMultinomialV3<ModelMetricsMultinomialGLM, ModelMetricsMultinomialGLMV3> {
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
  public ModelMetricsMultinomialV3 fillFromImpl(ModelMetricsMultinomial mms) {
    ModelMetricsMultinomialGLM modelMetrics = (ModelMetricsMultinomialGLM)mms;
    super.fillFromImpl(modelMetrics);
    this.AIC = modelMetrics._AIC;
    this.residual_deviance = modelMetrics._resDev;
    this.null_deviance = modelMetrics._nullDev;
    this.null_degrees_of_freedom = modelMetrics._nullDegressOfFreedom;
    this.residual_degrees_of_freedom = modelMetrics._residualDegressOfFreedom;
    return this;
  }

}
