package hex.aggregator;

import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsAggregator extends ModelMetricsUnsupervised {
  public ModelMetricsAggregator(Model model, Frame frame) {
    super(model, frame, 0, Double.NaN);
  }

  // Aggregator currently does not have any model metrics to compute during scoring
  public static class AggregatorModelMetrics extends MetricBuilderUnsupervised {
    public AggregatorModelMetrics(int dims) {
      _work = new double[dims];
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) { return preds; }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return m._output.addModelMetrics(new hex.aggregator.ModelMetricsAggregator(m, f));
    }
  }
}
