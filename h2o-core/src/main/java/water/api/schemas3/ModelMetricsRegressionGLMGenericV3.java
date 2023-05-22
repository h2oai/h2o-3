package water.api.schemas3;

import hex.ModelMetricsRegressionGLMGeneric;
import water.api.API;

public class ModelMetricsRegressionGLMGenericV3 extends ModelMetricsRegressionV3<ModelMetricsRegressionGLMGeneric, ModelMetricsRegressionGLMGenericV3> {
  @API(help = "residual deviance", direction = API.Direction.OUTPUT)
  public double residual_deviance;

  @API(help = "null deviance", direction = API.Direction.OUTPUT)
  public double null_deviance;

  @API(help = "AIC", direction = API.Direction.OUTPUT)
  public double AIC;
  
  @API(help = "log likelihood", direction = API.Direction.OUTPUT)
  public double loglikelihood;

  @API(help="null DOF", direction= API.Direction.OUTPUT)
  public long null_degrees_of_freedom;

  @API(help="residual DOF", direction= API.Direction.OUTPUT)
  public long residual_degrees_of_freedom;
  
  @API(direction = API.Direction.OUTPUT, help="coefficients_table")
  public TwoDimTableV3 coefficients_table; // Originally not part of metrics, put here to avoid GenericOutput having multiple output classes.
  
  @Override
  public ModelMetricsRegressionGLMGenericV3 fillFromImpl(ModelMetricsRegressionGLMGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    this.AIC = modelMetrics._AIC;
    this.loglikelihood = modelMetrics._loglikelihood;
    this.residual_deviance = modelMetrics._resDev;
    this.null_deviance = modelMetrics._nullDev;
    this.null_degrees_of_freedom = modelMetrics._nullDegressOfFreedom;
    this.residual_degrees_of_freedom = modelMetrics._residualDegressOfFreedom;
    coefficients_table = modelMetrics._coefficients_table != null ?new TwoDimTableV3().fillFromImpl(modelMetrics._coefficients_table) : null;
    return this;
  }

}
