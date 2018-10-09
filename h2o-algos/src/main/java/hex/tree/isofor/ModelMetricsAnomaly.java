package hex.tree.isofor;

import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsAnomaly extends ModelMetricsUnsupervised {

  public ModelMetricsAnomaly(Model model, Frame frame, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
  }

  // Anomaly Detection currently does not have any model metrics to compute during scoring
  public static class MetricBuilderAnomaly extends MetricBuilderUnsupervised {
    public MetricBuilderAnomaly() {
      _work = new double[2]; // just like regression
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) { return preds; }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return m.addModelMetrics(new ModelMetricsAnomaly(m, f, _customMetric));
    }
  }
}