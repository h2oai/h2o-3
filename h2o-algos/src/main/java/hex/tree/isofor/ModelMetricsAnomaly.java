package hex.tree.isofor;

import hex.CustomMetric;
import hex.Model;
import hex.ModelMetrics;
import hex.ModelMetricsUnsupervised;
import water.fvec.Frame;

public class ModelMetricsAnomaly extends ModelMetricsUnsupervised {

  public final double _mean_score;
  public final double _mean_normalized_score;

  public ModelMetricsAnomaly(Model model, Frame frame, CustomMetric customMetric,
                             long nobs, double totalScore, double totalNormScore) {
    super(model, frame, nobs, Double.NaN, customMetric);
    _mean_score = totalScore / nobs;
    _mean_normalized_score = totalNormScore / nobs;
  }

  public static class MetricBuilderAnomaly extends MetricBuilderUnsupervised<MetricBuilderAnomaly> {
    private double _total_score = 0;
    private double _total_norm_score = 0;
    private long _nobs = 0;

    public MetricBuilderAnomaly() {
      _work = new double[2];
    }

    @Override
    public double[] perRow(double[] preds, float[] dataRow, Model m) {
      if (preds[0] < 0)
        return preds;
      _total_norm_score += preds[0];
      _total_score += preds[1];
      _nobs++;
      return preds;
    }

    @Override
    public void reduce(MetricBuilderAnomaly mb) {
      _total_score += mb._total_score;
      _total_norm_score += mb._total_norm_score;
      _nobs += mb._nobs;
      super.reduce(mb);
    }

    @Override
    public ModelMetrics makeModelMetrics(Model m, Frame f, Frame adaptedFrame, Frame preds) {
      return m.addModelMetrics(new ModelMetricsAnomaly(m, f, _customMetric, _nobs, _total_score, _total_norm_score));
    }
  }
}