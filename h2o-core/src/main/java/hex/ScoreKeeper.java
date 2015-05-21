package hex;

import static hex.ModelMetricsMultinomial.getHitRatioTable;
import water.Iced;
import water.util.MathUtils;

/**
 * Low-weight keeper of scores
 * Solely intended for display (either direct or as helper to create scoring history TwoDimTable)
 * Not intended to store large AUC object or ConfusionMatrices, etc.
 */
public class ScoreKeeper extends Iced {
  public double _r2 = Double.NaN;
  public double _mse = Double.NaN;
  public double _logloss = Double.NaN;
  public double _AUC = Double.NaN;
  public double _classError = Double.NaN;
  public float[] _hitratio;

  public ScoreKeeper() {}
  public ScoreKeeper(double mse) { _mse = mse; }
  public ScoreKeeper(ModelMetrics mm) { fillFrom(mm); }

  public void fillFrom(ModelMetrics m) {
    if (m == null) return;
    _mse = m._MSE;
    if (m instanceof ModelMetricsSupervised) {
      _r2 = ((ModelMetricsSupervised)m).r2();
    }
    if (m instanceof ModelMetricsBinomial) {
      _logloss = ((ModelMetricsBinomial)m)._logloss;
      if (((ModelMetricsBinomial)m)._auc != null) {
        _AUC = ((ModelMetricsBinomial) m)._auc._auc;
        _classError = ((ModelMetricsBinomial) m)._auc.defaultErr();
      }
    }
    else if (m instanceof ModelMetricsMultinomial) {
      _logloss = ((ModelMetricsMultinomial)m)._logloss;
      _classError = ((ModelMetricsMultinomial)m)._cm.err();
      _hitratio = ((ModelMetricsMultinomial)m)._hit_ratios;
    }
  }
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("r2 is " + String.format("%5f",_r2) + ", MSE is " + String.format("%5f",_mse));
    if (!Double.isNaN(_logloss)) sb.append(", logloss is " + String.format("%5f",_logloss));
    if (!Double.isNaN(_AUC)) sb.append(", AUC is " + String.format("%5f",_AUC));
    if (!Double.isNaN(_classError)) sb.append(", classification error is " + String.format("%5f",_classError));
    if (_hitratio != null) sb.append("\n" + getHitRatioTable(_hitratio));
    return sb.toString();

  }
  @Override public boolean equals(Object obj) {
    if (! (obj instanceof ScoreKeeper)) return false;
    ScoreKeeper o = (ScoreKeeper)obj;
    return MathUtils.compare(_r2, o._r2, 1e-6, 1e-6)
            && MathUtils.compare(_mse, o._mse, 1e-6, 1e-6)
            && MathUtils.compare(_logloss, o._logloss, 1e-6, 1e-6)
            && MathUtils.compare(_classError, o._classError, 1e-6, 1e-6);
  }
}
