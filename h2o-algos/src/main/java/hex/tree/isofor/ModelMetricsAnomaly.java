package hex.tree.isofor;

import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsAnomaly extends ModelMetricsUnsupervised {

  public final double _mean_score;

  public ModelMetricsAnomaly(Model model, Frame frame, CustomMetric customMetric, double totalScore) {
    super(model, frame, frame.numRows(), Double.NaN, customMetric);
    _mean_score = totalScore / frame.numRows();
    System.out.println(_mean_score);
  }

  // Anomaly Detection currently does not have any model metrics to compute during scoring
  public static class MetricBuilderAnomaly extends MetricBuilderUnsupervised<MetricBuilderAnomaly> {
    private double _total_score = 0;

    public MetricBuilderAnomaly() {
      _work = new double[2]; // just like regression
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) {
      _total_score += preds[0];
      return preds;
    }

    @Override
    public void reduce(MetricBuilderAnomaly mb) {
      _total_score += mb._total_score;
      super.reduce(mb);
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return m.addModelMetrics(new ModelMetricsAnomaly(m, f, _customMetric, _total_score));
    }
  }
}