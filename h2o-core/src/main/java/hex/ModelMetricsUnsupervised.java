package hex;

import water.fvec.Frame;

public class ModelMetricsUnsupervised extends ModelMetrics {
  public ModelMetricsUnsupervised(Model model, Frame frame, double MSE) {
    super(model, frame, MSE, null);
  }

  public static abstract class MetricBuilderUnsupervised extends MetricBuilder {
  }
}
