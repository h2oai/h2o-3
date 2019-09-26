package water.api.schemas3;

import hex.ModelMetricHGLMGaussianGaussianGeneric;

public class ModelMetricsHGLMGaussianGaussianGenericV3<I extends ModelMetricHGLMGaussianGaussianGeneric, S extends ModelMetricsHGLMGaussianGaussianGenericV3<I, S>> 
extends ModelMetricsHGLMGenericV3<I,S> {
  
  @Override
  public S fillFromImpl(ModelMetricHGLMGaussianGaussianGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    return (S) this;
  }
}
