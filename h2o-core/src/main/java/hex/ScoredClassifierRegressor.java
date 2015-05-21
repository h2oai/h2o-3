package hex;

import water.Iced;
import water.util.MathUtils;

/** Train and validation errors per-tree (scored).  Zero index is the no-tree
 *  error, guessing only the class distribution (if applicable). Not all trees are
 *  scored, NaN values represents trees not scored. */
public class ScoredClassifierRegressor extends Iced {
  public double _r2 = Double.NaN;
  public double _mse = Double.NaN;
  public double _logloss = Double.NaN;
  public double _AUC = Double.NaN;
  public double _classError = Double.NaN;

  public ScoredClassifierRegressor() {}
  public ScoredClassifierRegressor(double mse) { _mse = mse; }


  public void fillFrom(ModelMetrics m) {
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
    }
  }
  @Override public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append("training r2 is " + String.format("%5f",_r2) + ", MSE is " + String.format("%5f",_mse));
    if (!Double.isNaN(_logloss)) sb.append(", logloss is " + String.format("%5f",_logloss));
    if (!Double.isNaN(_AUC)) sb.append(", AUC is " + String.format("%5f",_AUC));
    if (!Double.isNaN(_classError)) sb.append(", classification error is " + String.format("%5f",_classError));
    return sb.toString();

  }
  @Override public boolean equals(Object obj) {
    if (! (obj instanceof ScoredClassifierRegressor)) return false;
    ScoredClassifierRegressor o = (ScoredClassifierRegressor)obj;
    return MathUtils.compare(_r2, o._r2, 1e-6, 1e-6)
            && MathUtils.compare(_mse, o._mse, 1e-6, 1e-6)
            && MathUtils.compare(_logloss, o._logloss, 1e-6, 1e-6)
            && MathUtils.compare(_classError, o._classError, 1e-6, 1e-6);
  }
}
