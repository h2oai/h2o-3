package hex;

import water.fvec.Frame;

public class ModelMetricHGLMGaussianGaussianGeneric extends ModelMetricsHGLMGeneric {
  public ModelMetricHGLMGaussianGaussianGeneric(Model model, Frame frame, long nobs, double mse, String[] domain, 
                                                double sigma, CustomMetric customMetric, double[] sefe, double[] sere,
                                                double varfix, double[] varranef, boolean converge, double dfrefe, 
                                                double[] summvc1, double[][] summvc2, double hlik, double pvh, 
                                                double pbvh, double cAIC, long bad, double sumEtaDiffSq, 
                                                double convergence, int[] randC, double[] fixef, double[] ranef, 
                                                int iteration) {
    super(model, frame, nobs, mse, domain, sigma, customMetric, sefe, sere, varfix, varranef, converge, dfrefe,
            summvc1, summvc2, hlik, pvh, pbvh, cAIC, bad, sumEtaDiffSq, convergence, randC, fixef, ranef, iteration);
  }
}
