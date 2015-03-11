package water.api;

import hex.ModelMetricsBinomialGLM;
import water.api.API.Direction;

public class ModelMetricsBinomialGLMV3 extends ModelMetricsBinomialBaseV3<ModelMetricsBinomialGLM, ModelMetricsBinomialGLMV3> {
  @API(help="residual deviance",direction = Direction.OUTPUT)
  public double residual_deviance;

  @API(help="null deviance",direction = Direction.OUTPUT)
  public double null_deviance;

  @API(help="aic",direction = Direction.OUTPUT)
  public double aic;

  @Override
  public ModelMetricsBinomialGLMV3 fillFromImpl(ModelMetricsBinomialGLM modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.aic = modelMetrics._aic;
    this.residual_deviance = modelMetrics._resDev;
    this.null_deviance = modelMetrics._nullDev;
    return this;
  }

}
