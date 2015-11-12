package hex;

import water.H2O;
import water.Iced;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;

/**
 * Low-weight keeper of scores
 * Solely intended for display (either direct or as helper to create scoring history TwoDimTable)
 * Not intended to store large AUC object or ConfusionMatrices, etc.
 */
public class ScoreKeeper extends Iced {
  public double _r2 = Double.NaN;
  public double _mean_residual_deviance = Double.NaN;
  public double _mse = Double.NaN;
  public double _logloss = Double.NaN;
  public double _AUC = Double.NaN;
  public double _classError = Double.NaN;
  public float[] _hitratio;

  public ScoreKeeper() {}
  public ScoreKeeper(double mse) { _mse = mse; }
  public ScoreKeeper(ModelMetrics mm) { fillFrom(mm); }
  public boolean isEmpty() {
    return Double.isNaN(_mse) && Double.isNaN(_logloss); // at least one of them should always be filled
  }

  public void fillFrom(ModelMetrics m) {
    if (m == null) return;
    _mse = m._MSE;
    if (m instanceof ModelMetricsSupervised) {
      _r2 = ((ModelMetricsSupervised)m).r2();
    }
    if (m instanceof ModelMetricsRegression) {
      _mean_residual_deviance = ((ModelMetricsRegression)m)._mean_residual_deviance;
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
            && MathUtils.compare(_mean_residual_deviance, o._mean_residual_deviance, 1e-6, 1e-6)
            && MathUtils.compare(_mse, o._mse, 1e-6, 1e-6)
            && MathUtils.compare(_logloss, o._logloss, 1e-6, 1e-6)
            && MathUtils.compare(_classError, o._classError, 1e-6, 1e-6);
  }

  public enum StoppingMetric { AUTO, deviance, logloss, MSE, AUC, r2, misclassification}

  public static boolean earlyStopping(ScoreKeeper[] sk, int k, boolean classification, StoppingMetric criterion, double rel_improvement) {
    if (k == 0) return false;
    int len = sk.length - 1; //how many "full"/"conservative" scoring events we have (skip the first)
    if (len < 2*k) return false; //need at least k for SMA and another k to tell whether the model got better or not

    if (criterion==StoppingMetric.AUTO) {
      criterion = classification ? StoppingMetric.logloss : StoppingMetric.deviance;
    }

    boolean moreIsBetter = (criterion == StoppingMetric.AUC || criterion == StoppingMetric.r2);
    double movingAvg[] = new double[k+1]; //need one moving average value for the last k+1 scoring events
    double lastBeforeK = moreIsBetter ? -Double.MAX_VALUE : Double.MAX_VALUE;
    double bestInLastK = moreIsBetter ? -Double.MAX_VALUE : Double.MAX_VALUE;
    for (int i=0;i<movingAvg.length;++i) {
      movingAvg[i] = 0;
      // compute k+1 simple moving averages of window size k
      // need to go back 2*k steps

      // Example: 20 scoring events, k=3
      // need to go back from idx 19 to idx 14
      // movingAvg[0] is based on scoring events indices 14,15,16 <- reference
      // movingAvg[1] is based on scoring events indices 15,16,17 <- first "new" smooth score
      // movingAvg[2] is based on scoring events indices 16,17,18 <- second "new" smooth score
      // movingAvg[3] is based on scoring events indices 17,18,19 <- third "new" smooth score

      // Example: 18 scoring events, k=2
      // need to go back from idx 17 to idx 14
      // movingAvg[0] is based on scoring events indices 14,15 <- reference
      // movingAvg[1] is based on scoring events indices 15,16 <- first "new" smooth score
      // movingAvg[2] is based on scoring events indices 16,17 <- second "new" smooth score

      int startIdx = sk.length-2*k+i;
      for (int j = 0; j < k; ++j) {
        ScoreKeeper skj = sk[startIdx+j];
        double val;
        switch (criterion) {
          case AUC:
            val = skj._AUC;
            break;
          case MSE:
            val = skj._mse;
            break;
          case deviance:
            val = skj._mean_residual_deviance;
            break;
          case logloss:
            val = skj._logloss;
            break;
          case r2:
            val = skj._r2;
            break;
          case misclassification:
            val = skj._classError;
            break;
          default:
            throw H2O.unimpl("Undefined stopping criterion.");
        }
        movingAvg[i] += val;
      }
      movingAvg[i]/=k;
      if (Double.isNaN(movingAvg[i])) return false;
      if (i==0)
        lastBeforeK = movingAvg[i];
      else
        bestInLastK = moreIsBetter ? Math.max(movingAvg[i], bestInLastK) : Math.min(movingAvg[i], bestInLastK);
    }
    // zero-crossing could be for residual deviance or r^2 -> mark it not yet converged, avoid division by 0 or weird relative improvements math below
    if (Math.signum(ArrayUtils.maxValue(movingAvg)) != Math.signum(ArrayUtils.minValue(movingAvg))) return false;
    if (Math.signum(bestInLastK) != Math.signum(lastBeforeK)) return false;
    assert(lastBeforeK != Double.MAX_VALUE);
    assert(bestInLastK != Double.MAX_VALUE);
    Log.info("Moving averages (length " + k + ") of last " + (k+1) + " " + criterion.toString() + " metrics: " + Arrays.toString(movingAvg));

    double ratio = bestInLastK / lastBeforeK;
    if (Double.isNaN(ratio)) return false;
    boolean improved = moreIsBetter ? ratio > 1+rel_improvement : ratio < 1-rel_improvement;
    Log.info("Checking model convergence with " + criterion.toString() + " metric: " + lastBeforeK + " --> " + bestInLastK + (improved ? " (still improving)." : " (converged)."));
    return !improved;
  }


}
