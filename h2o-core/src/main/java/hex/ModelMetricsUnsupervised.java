package hex;

import water.fvec.Frame;

public class ModelMetricsUnsupervised extends ModelMetrics {
  public ModelMetricsUnsupervised(Model model, Frame frame, long nobs, double MSE, CustomMetric customMetric) {
    super(model, frame, nobs, MSE, null, customMetric);
  }

  public static abstract class MetricBuilderUnsupervised<T extends MetricBuilderUnsupervised<T>>
          extends MetricBuilder<T> {}
}
