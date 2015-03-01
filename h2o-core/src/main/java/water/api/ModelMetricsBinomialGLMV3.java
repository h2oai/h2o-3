package water.api;

import hex.ModelMetricsBinomialGLM;
import water.api.API.Direction;

public class ModelMetricsBinomialGLMV3 extends ModelMetricsBinomialBaseV3<ModelMetricsBinomialGLM, ModelMetricsBinomialGLMV3> {
  @API(help="residual deviance",direction = Direction.OUTPUT)
  public double residualDeviance;

  @API(help="null deviance",direction = Direction.OUTPUT)
  public double nullDeviance;

  @API(help="aic",direction = Direction.OUTPUT)
  public double aic;

  @Override
  public ModelMetricsBinomialGLMV3 fillFromImpl(ModelMetricsBinomialGLM modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.aic = modelMetrics._aic;
    this.residualDeviance = modelMetrics._resDev;
    this.nullDeviance = modelMetrics._nullDev;
    return this;
  }

}
