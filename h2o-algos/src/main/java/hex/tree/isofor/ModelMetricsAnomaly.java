package hex.tree.isofor;

import hex.*;
import water.fvec.Frame;

public class ModelMetricsAnomaly extends ModelMetricsUnsupervised implements ScoreKeeper.ScoreKeeperAware {

  /**
   * The raw number that an algorithm is using to count final anomaly score.
   * E.g. raw number for Isolation Forest algorithm to count final anomaly score is mean path length
   * of the observation (input row) from root to a leaf.
   */
  public final double _mean_score;

  /**
   * Mean normalized score should be (but not necessary is) a number between 0 and 1. Try to follow convention that higher number means
   * "more anomalous" observation (input row) and number more close to 0 means standard (not anomalous) observation (input row).
   *
   * Always refer to the algorithm's documentation for proper definition of this number.
   * E.g. formula for normalization of Isolation Forest's score is different from the formula for Extended Isolation Forest.
   */
  public final double _mean_normalized_score;

  public ModelMetricsAnomaly(Model model, Frame frame, CustomMetric customMetric,
                             long nobs, double totalScore, double totalNormScore,
                             String description) {
    super(model, frame, nobs, description, customMetric);
    _mean_score = totalScore / nobs;
    _mean_normalized_score = totalNormScore / nobs;
  }

  @Override
  public void fillTo(ScoreKeeper sk) {
    sk._anomaly_score = _mean_score;
    sk._anomaly_score_normalized = _mean_normalized_score;
  }

  @Override
  protected StringBuilder appendToStringMetrics(StringBuilder sb) {
    sb.append(" Number of Observations: ").append(_nobs).append("\n");
    sb.append(" Mean Score: ").append(_mean_score).append("\n");
    sb.append(" Mean Normalized Anomaly Score: ").append(_mean_normalized_score).append("\n");
    return sb;
  }

  public static class MetricBuilderAnomaly extends MetricBuilderUnsupervised<MetricBuilderAnomaly> {
    private transient String _description;
    private double _total_score = 0;
    private double _total_norm_score = 0;
    private long _nobs = 0;

    public MetricBuilderAnomaly() {
      this("", false);
    }
    
    public MetricBuilderAnomaly(String description, boolean outputAnomalyFlag) {
      _work = new double[outputAnomalyFlag ? 3 : 2];
      _description = description;
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
    public ModelMetrics makeModelMetrics(Model m, Frame f) {
      return m.addModelMetrics(new ModelMetricsAnomaly(m, f, _customMetric, _nobs, _total_score, _total_norm_score, _description));
    }
  }
}
