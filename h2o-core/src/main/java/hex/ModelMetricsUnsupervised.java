package hex;

import water.fvec.Frame;

public class ModelMetricsUnsupervised extends ModelMetrics {
  public ModelMetricsUnsupervised(Model model, Frame frame, long nobs, double MSE) {
    super(model, frame, nobs, MSE, null);
  }

  public static abstract class MetricBuilderUnsupervised extends MetricBuilder {
  }
}
