package hex;

        import water.fvec.Frame;

public class ModelMetricsRegressionHGLMGeneric extends ModelMetricsRegressionHGLM {
  public ModelMetricsRegressionHGLMGeneric(Model model, Frame frame, long nobs, double sigma, double loglikelihood, 
                                           CustomMetric customMetric, int iter, double[] beta, double[][] ubeta,
                                           double[][] tmat, double varResidual, double mse, double mse_fixed, double mae,
                                           double rmsle, double meanresidualdeviance, double aic) {
    super(model, frame, nobs, sigma, loglikelihood, customMetric, iter, beta, ubeta, tmat, varResidual, mse, mse_fixed, 
            mae, rmsle, meanresidualdeviance, aic);
  }
}
