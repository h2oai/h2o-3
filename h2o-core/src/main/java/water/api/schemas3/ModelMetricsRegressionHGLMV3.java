package water.api.schemas3;

import hex.ModelMetricsRegressionHGLM;
import water.api.API;

public class ModelMetricsRegressionHGLMV3<I extends ModelMetricsRegressionHGLM, S extends ModelMetricsRegressionHGLMV3<I, S>>
        extends ModelMetricsBaseV3<I, S> {
  @API(help="fixed coefficient)", direction=API.Direction.OUTPUT)
  public double[] beta;       // dispersion parameter of the mean model (residual variance for LMM)

  @API(help="random coefficients", direction=API.Direction.OUTPUT)
  public double[][] ubeta;     // dispersion parameter of the random effects (variance of random effects for GLMM)
 
 @API(help="log likelihood", direction=API.Direction.OUTPUT)
  public double log_likelihood;         // log h-likelihood
  
  @API(help="interclass correlation", direction=API.Direction.OUTPUT)
  public double[] icc;
  
  @API(help="iterations taken to build model", direction=API.Direction.OUTPUT)
  public int iterations;
  
  @API(help="covariance matrix of random effects", direction=API.Direction.OUTPUT)
  public double[][] tmat;
  
  @API(help="variance of residual error", direction=API.Direction.OUTPUT)
  public double var_residual;
  
  @API(help="mean square error of fixed effects only", direction=API.Direction.OUTPUT)
  public double mse_fixed;
  
  @Override
  public S fillFromImpl(ModelMetricsRegressionHGLM modelMetrics) {
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
