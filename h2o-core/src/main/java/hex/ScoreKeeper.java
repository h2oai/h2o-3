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
  public double _residual_deviance = Double.NaN;
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
    if (m instanceof ModelMetricsRegression) {
      _residual_deviance = ((ModelMetricsRegression)m)._residual_deviance;
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

  /**
   * Light-weight print of metrics to a String, meant to take least amount of lines possible
   * @return String containing metrics printed for human consumption
   */
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("r2 is " + String.format("%5f",_r2) + ", MSE is " + String.format("%5f",_mse));
    if (!Double.isNaN(_residual_deviance)) sb.append(", residual deviance is " + String.format("%5f",_residual_deviance));
    if (!Double.isNaN(_logloss)) sb.append(", logloss is " + String.format("%5f",_logloss));
    if (!Double.isNaN(_AUC)) sb.append(", AUC is " + String.format("%5f",_AUC));
    if (!Double.isNaN(_classError)) sb.append(", classification error is " + String.format("%5f",_classError));
    if (_hitratio != null) sb.append("\n" + getHitRatioTable(_hitratio));
    return sb.toString();

  }

  /**
   * Compare this ScoreKeeper with that ScoreKeeper
   * @param that
   * @return true if they are equal (up to 1e-6 absolute and relative error, or both contain NaN for the same values)
   */
  @Override public boolean equals(Object that) {
    if (! (that instanceof ScoreKeeper)) return false;
    ScoreKeeper o = (ScoreKeeper)that;
    if (_hitratio == null && ((ScoreKeeper) that)._hitratio != null) return false;
    if (_hitratio != null && ((ScoreKeeper) that)._hitratio == null) return false;
    if (_hitratio != null && ((ScoreKeeper) that)._hitratio != null) {
      if (_hitratio.length != ((ScoreKeeper) that)._hitratio.length) return false;
      for (int i=0; i<_hitratio.length; ++i) {
        if (!MathUtils.compare(_hitratio[i], ((ScoreKeeper) that)._hitratio[i], 1e-6, 1e-6)) return false;
      }
    }
    return MathUtils.compare(_r2, o._r2, 1e-6, 1e-6)
            && MathUtils.compare(_residual_deviance, o._residual_deviance, 1e-6, 1e-6)
            && MathUtils.compare(_mse, o._mse, 1e-6, 1e-6)
            && MathUtils.compare(_logloss, o._logloss, 1e-6, 1e-6)
            && MathUtils.compare(_classError, o._classError, 1e-6, 1e-6);
  }
}
