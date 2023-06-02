package water.api.schemas3;

import hex.ModelMetricsOrdinalGLMGeneric;
import water.api.API;

public class ModelMetricsOrdinalGLMGenericV3<I extends ModelMetricsOrdinalGLMGeneric, S extends ModelMetricsOrdinalGLMGenericV3<I, S>>
        extends ModelMetricsOrdinalGenericV3<I, S> {
  @API(help = "residual deviance", direction = API.Direction.OUTPUT)
  public double residual_deviance;

  @API(help = "null deviance", direction = API.Direction.OUTPUT)
  public double null_deviance;

  @API(help = "AIC", direction = API.Direction.OUTPUT)
  public double AIC;
  
  @API(help = "log likelihood", direction = API.Direction.OUTPUT)
  public double loglikelihood;

  @API(help = "null DOF", direction = API.Direction.OUTPUT)
  public long null_degrees_of_freedom;

  @API(help = "residual DOF", direction = API.Direction.OUTPUT)
  public long residual_degrees_of_freedom;

  @API(direction = API.Direction.OUTPUT, help="coefficients_table")
  public TwoDimTableV3 coefficients_table; // Originally not part of metrics, put here to avoid GenericOutput having multiple output classes.

  @Override
  public S fillFromImpl(I modelMetrics) {
    super.fillFromImpl(modelMetrics);

    this.AIC = modelMetrics._AIC;
    this.loglikelihood = modelMetrics._loglikelihood;
    this.residual_deviance = modelMetrics._resDev;
    this.null_deviance = modelMetrics._nullDev;
    this.null_degrees_of_freedom = modelMetrics._nullDegreesOfFreedom;
    this.residual_degrees_of_freedom = modelMetrics._residualDegreesOfFreedom;
    this.coefficients_table = new TwoDimTableV3().fillFromImpl(modelMetrics._coefficients_table);
    this.r2 = modelMetrics._r2;
        
    return (S) this;
  }
}
