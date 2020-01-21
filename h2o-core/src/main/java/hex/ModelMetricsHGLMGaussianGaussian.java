package hex;

import water.fvec.Frame;
import java.util.Arrays;

public class ModelMetricsHGLMGaussianGaussian extends ModelMetricsHGLM implements GLMMetrics {
  public ModelMetricsHGLMGaussianGaussian(Model model, Frame frame, long nobs, double mse, String[] domain, double sigma,
                                          CustomMetric customMetric, double[] sefe, double[] sere, double varfix, double[] varranef,
                                          boolean converge, double dfrefe, double[] summvc1, double[][] summvc2, double hlik,
                                          double pvh, double pbvh, double cAIC, long bad, double sumEtaDiffSq, 
                                          double convergence, int[] RandC, double[] fixef, double[] ranef, int iteration) {
    super(model, frame, nobs, mse, domain, sigma, customMetric, sefe, sere, varfix, varranef, converge, dfrefe, 
            summvc1, summvc2, hlik, pvh, pbvh, cAIC, bad, sumEtaDiffSq, convergence, RandC, fixef, ranef, iteration);
  }
  
  @Override
  public double residual_deviance() {
    return Double.NaN;
  }

  @Override
  public double null_deviance() {
    return Double.NaN;
  }

  @Override
  public long residual_degrees_of_freedom() { return 0;}

  @Override
  public long null_degrees_of_freedom() {
    return 0;
  }
  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    return sb.toString();
  }
  
  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ModelMetricsHGLMGaussianGaussian))
      return false;
    ModelMetricsHGLMGaussianGaussian mm = (ModelMetricsHGLMGaussianGaussian)o;
    boolean isEquals = Arrays.equals(_sefe, mm._sefe) && Arrays.equals(_sere, mm._sere)&& _varfix ==mm._varfix &&
            _varranef ==mm._varranef &&_converge==mm._converge && Arrays.equals(_summvc1, mm._summvc1) && _hlik==mm._hlik
            && _pvh==mm._pvh && _pbvh==mm._pbvh && _caic==mm._caic && _bad==mm._bad && 
            _sumetadiffsquare ==mm._sumetadiffsquare && _convergence==mm._convergence;
    if (!isEquals)
      return false;
    for (int k = 0; k < _summvc2.length; k++) {
      if (!Arrays.equals(_summvc2[k], mm._summvc2[k]))
        return false;
    }
    return true;
  }
}
