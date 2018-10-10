package hex.tree.isofor;

import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsAnomaly extends ModelMetricsUnsupervised {

  double _min_raw;
  double _max_raw;

  public ModelMetricsAnomaly(Model model, Frame frame, CustomMetric customMetric, double min_raw, double max_raw) {
    super(model, frame, 0, Double.NaN, customMetric);
    _min_raw = min_raw;
    _max_raw = max_raw;
  }

  // Anomaly Detection currently does not have any model metrics to compute during scoring
  public static class MetricBuilderAnomaly extends MetricBuilderUnsupervised<MetricBuilderAnomaly> {
    private double _min_raw = Double.MAX_VALUE;
    private double _max_raw = 0;

    public MetricBuilderAnomaly() {
      _work = new double[2]; // just like regression
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) {
      _min_raw = Math.min(preds[0], _min_raw);
      _max_raw = Math.max(preds[0], _max_raw);
      return preds;
    }

    @Override
    public void reduce(MetricBuilderAnomaly mb) {
      _min_raw = Math.min(_min_raw, mb._min_raw);
      _max_raw = Math.max(_max_raw, mb._max_raw);
      super.reduce(mb);
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return m.addModelMetrics(new ModelMetricsAnomaly(m, f, _customMetric, _min_raw, _max_raw));
    }
  }
}