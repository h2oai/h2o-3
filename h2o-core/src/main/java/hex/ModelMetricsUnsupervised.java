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
          extends MetricBuilder<T> {

    @Override
    public final ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return makeModelMetrics(m, f);
    }

    public abstract ModelMetrics makeModelMetrics(Model m, Frame f);
    
  }

  public static abstract class IndependentMetricBuilderUnsupervised<T extends IndependentMetricBuilderUnsupervised<T>>
          extends IndependentMetricBuilder<T> {
  }
}
