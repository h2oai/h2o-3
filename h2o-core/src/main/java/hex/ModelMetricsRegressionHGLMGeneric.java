package hex;

        import water.fvec.Frame;

public class ModelMetricsRegressionHGLMGeneric extends ModelMetricsRegressionHGLM {
  public ModelMetricsRegressionHGLMGeneric(Model model, Frame frame, long nobs, String[] domain, double sigma,
                                           CustomMetric customMetric, int iter, double[] beta, double[][] ubeta,
                                           double[][] tmat, double varResidual, double mse, double mse_fixed,
                                           double[][] yMinusXFixTimesZ, double[][][] arjtarj) {
    super(model, frame, nobs, domain, sigma, customMetric, iter, beta, ubeta, tmat, varResidual, mse, mse_fixed, 
            yMinusXFixTimesZ, arjtarj);
  }
}
