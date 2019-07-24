package water.api.schemas3;

import hex.ModelMetricsRegressionGLMGeneric;
import water.api.API;

public class ModelMetricsRegressionGLMGenericV3 extends ModelMetricsRegressionGLMV3<ModelMetricsRegressionGLMGeneric, ModelMetricsRegressionGLMGenericV3> {
  @API(direction = API.Direction.OUTPUT, help="coefficients_table")
  public TwoDimTableV3 _coefficients_table; // Originally not part of metrics, put here to avoid GenericOutput having multiple output classes.
  
  @Override
  public ModelMetricsRegressionGLMGenericV3 fillFromImpl(ModelMetricsRegressionGLMGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    r2 = modelMetrics.r2();
    _coefficients_table = modelMetrics._coefficients_table != null ?new TwoDimTableV3().fillFromImpl(modelMetrics._coefficients_table) : null;
    return this;
  }

}
