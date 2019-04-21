package hex;

import water.fvec.Frame;

public class ModelMetricsUnsupervised extends ModelMetrics {
  public ModelMetricsUnsupervised(Model model, Frame frame, long nobs, double MSE, CustomMetric customMetric) {
    super(model, frame, nobs, MSE, null, customMetric);
  }

  public ModelMetricsUnsupervised(Model model, Frame frame, long nobs, String description, CustomMetric customMetric) {
    super(model, frame, nobs, Double.NaN, description, customMetric);
  }

  public static abstract class MetricBuilderUnsupervised<T extends MetricBuilderUnsupervised<T>>
          extends MetricBuilder<T> {}
}
