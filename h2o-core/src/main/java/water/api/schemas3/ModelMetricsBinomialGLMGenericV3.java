package water.api.schemas3;

import hex.ModelMetricsBinomialGLMGeneric;
import water.api.API;

public class ModelMetricsBinomialGLMGenericV3<I extends ModelMetricsBinomialGLMGeneric, S extends ModelMetricsBinomialGLMGenericV3<I, S>>
        extends ModelMetricsBinomialGenericV3<I, S> {

  @API(help="residual deviance",direction = API.Direction.OUTPUT)
  public double residual_deviance;

  @API(help="null deviance",direction = API.Direction.OUTPUT)
  public double null_deviance;

  @API(help="AIC",direction = API.Direction.OUTPUT)
  public double AIC;

  @API(help="null DOF", direction= API.Direction.OUTPUT)
  public long null_degrees_of_freedom;

  @API(help="residual DOF", direction= API.Direction.OUTPUT)
  public long residual_degrees_of_freedom;

  @Override
  public S fillFromImpl(ModelMetricsBinomialGLMGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    
    this.AIC = modelMetrics._AIC;
    this.residual_deviance = modelMetrics._resDev;
    this.null_deviance = modelMetrics._nullDev;
    this.null_degrees_of_freedom = modelMetrics._nullDegressOfFreedom;
    this.residual_degrees_of_freedom = modelMetrics._residualDegressOfFreedom;
    

    return (S) this;
  }
}
