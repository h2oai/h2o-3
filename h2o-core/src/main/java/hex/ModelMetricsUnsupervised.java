package hex;

import water.fvec.Frame;

public class ModelMetricsUnsupervised extends ModelMetrics {
  public ModelMetricsUnsupervised(Model model, Frame frame) {
    super(model, frame);
  }

  public static abstract class MetricBuilderUnsupervised extends MetricBuilder {
    abstract public float[] perRow( float[] dataRow, float[] preds, Model m );

    // Having computed a MetricBuilder, this method fills in a ModelMetrics
    abstract public ModelMetrics makeModelMetrics( Model m, Frame f, double sigma);
  }
}
