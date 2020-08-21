package water.api.schemas3;

import hex.ModelMetricsHGLMGeneric;

public class ModelMetricsHGLMGenericV3<I extends ModelMetricsHGLMGeneric, S extends ModelMetricsHGLMGenericV3<I, S>> extends ModelMetricsHGLMV3<I,S> {

@Override
  public S fillFromImpl(ModelMetricsHGLMGeneric modelMetric) {
    super.fillFromImpl(modelMetric);
    return (S) this;
  }
}
