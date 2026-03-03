package water.api.schemas3;

import hex.ModelMetricsRegressionHGLMGeneric;

public class ModelMetricsRegressionHGLMGenericV3<I extends ModelMetricsRegressionHGLMGeneric, S extends ModelMetricsRegressionHGLMGenericV3<I, S>> 
        extends ModelMetricsRegressionHGLMV3<I,S> {

  @Override
  public S fillFromImpl(ModelMetricsRegressionHGLMGeneric modelMetrics) {
    super.fillFromImpl(modelMetrics);
    log_likelihood = modelMetrics._log_likelihood;
    icc = modelMetrics._icc;
    beta = modelMetrics._beta;
    ubeta = modelMetrics._ubeta;
    iterations = modelMetrics._iterations;
    tmat = modelMetrics._tmat;
    var_residual = modelMetrics._var_residual;
    mse_fixed = modelMetrics._mse_fixed;
    return (S) this;
  }
}
