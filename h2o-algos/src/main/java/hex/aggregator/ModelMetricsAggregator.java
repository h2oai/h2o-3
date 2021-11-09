package hex.aggregator;

import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsAggregator extends ModelMetricsUnsupervised {
  public ModelMetricsAggregator(Model model, Frame frame, CustomMetric customMetric) {
    super(model, frame, 0, Double.NaN, customMetric);
  }

  // Aggregator currently does not have any model metrics to compute during scoring
  public static class AggregatorModelMetrics extends MetricBuilderUnsupervised {
    public AggregatorModelMetrics(int dims) {
      _work = new double[dims];
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) { return preds; }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      return m.addModelMetrics(new hex.aggregator.ModelMetricsAggregator(m, f, _customMetric));
    }
  }
}
