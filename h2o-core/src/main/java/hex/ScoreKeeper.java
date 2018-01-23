package hex;

import water.H2O;
import water.Iced;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;
import java.util.Comparator;

import static hex.ScoreKeeper.StoppingMetric.deviance;

/**
 * Low-weight keeper of scores
 * solely intended for display (either direct or as helper to create scoring history TwoDimTable).
 * Not intended to store large AUC object or ConfusionMatrices, etc.
 */
public class ScoreKeeper extends Iced {
  public double _mean_residual_deviance = Double.NaN;
  public double _mse = Double.NaN;
  public double _rmse = Double.NaN;
  public double _mae = Double.NaN;
  public double _rmsle = Double.NaN;
  public double _logloss = Double.NaN;
  public double _AUC = Double.NaN;
  public double _classError = Double.NaN;
  public double _mean_per_class_error = Double.NaN;
  public double _custom_metric = Double.NaN;
  public float[] _hitratio;
  public double _lift = Double.NaN; //Lift in top group

  public ScoreKeeper() {}

  /**
   * Keep score of mean squared error <i>only</i>.
   * @param mse
   */
  public ScoreKeeper(double mse) { _mse = mse; }

  /**
   * Keep score of a given ModelMetrics.
   * @param mm ModelMetrics to keep track of.
   */
  public ScoreKeeper(ModelMetrics mm) { fillFrom(mm); }

  /**
   * Keep score for a model using its validation_metrics if available and training_metrics if not.
   * @param m model for which we should keep score
   */
  public ScoreKeeper(Model m) {
    if (null == m) throw new H2OIllegalArgumentException("model", "ScoreKeeper(Model model)", null);
    if (null == m._output) throw new H2OIllegalArgumentException("model._output", "ScoreKeeper(Model model)", null);


    if (null != m._output._cross_validation_metrics) {
      fillFrom(m._output._cross_validation_metrics);
    } else if (null != m._output._validation_metrics) {
      fillFrom(m._output._validation_metrics);
    } else {
      fillFrom(m._output._training_metrics);
    }
  }

  public boolean isEmpty() {
    return Double.isNaN(_mse) && Double.isNaN(_logloss); // at least one of them should always be filled
  }

  public void fillFrom(ModelMetrics m) {
    if (m == null) return;
    _mse = m._MSE;
    _rmse = m.rmse();
    if (m instanceof ModelMetricsRegression) {
      _mean_residual_deviance = ((ModelMetricsRegression)m)._mean_residual_deviance;
      _mae = ((ModelMetricsRegression)m)._mean_absolute_error;
      _rmsle = ((ModelMetricsRegression)m)._root_mean_squared_log_error;
    }
    if (m instanceof ModelMetricsBinomial) {
      _logloss = ((ModelMetricsBinomial)m)._logloss;
      if (((ModelMetricsBinomial)m)._auc != null) {
        _AUC = ((ModelMetricsBinomial) m)._auc._auc;
        _classError = ((ModelMetricsBinomial) m)._auc.defaultErr();
        _mean_per_class_error = ((ModelMetricsBinomial)m).mean_per_class_error();
      }
      GainsLift gl = ((ModelMetricsBinomial)m)._gainsLift;
      if (gl != null && gl.response_rates != null && gl.response_rates.length > 0) {
        _lift = gl.response_rates[0] / gl.avg_response_rate;
      }
    }
    else if (m instanceof ModelMetricsMultinomial) {
      _logloss = ((ModelMetricsMultinomial)m)._logloss;
      _classError = ((ModelMetricsMultinomial)m)._cm.err();
      _mean_per_class_error = ((ModelMetricsMultinomial)m).mean_per_class_error();
      _hitratio = ((ModelMetricsMultinomial)m)._hit_ratios;
    } else if (m instanceof ModelMetricsOrdinal) {
      _logloss = ((ModelMetricsOrdinal)m)._logloss;
      _classError = ((ModelMetricsOrdinal)m)._cm.err();
      _mean_per_class_error = ((ModelMetricsOrdinal)m).mean_per_class_error();
      _hitratio = ((ModelMetricsOrdinal)m)._hit_ratios;
    }
    if (m._custom_metric != null )
    _custom_metric =  m._custom_metric.value;
  }

  public enum StoppingMetric { AUTO, deviance, logloss, MSE, RMSE,MAE,RMSLE, AUC, lift_top_group, misclassification, mean_per_class_error, custom}
  public static boolean moreIsBetter(StoppingMetric criterion) {
    return (criterion == StoppingMetric.AUC || criterion == StoppingMetric.lift_top_group);
  }

  /** Based on the given array of ScoreKeeper and stopping criteria should we stop early? */
  public static boolean stopEarly(ScoreKeeper[] sk, int k, boolean classification, StoppingMetric criterion, double rel_improvement, String what, boolean verbose) {
    if (k == 0) return false;
    int len = sk.length - 1; //how many "full"/"conservative" scoring events we have (skip the first)
    if (len < 2*k) return false; //need at least k for SMA and another k to tell whether the model got better or not

    if (criterion==StoppingMetric.AUTO) {
      criterion = classification ? StoppingMetric.logloss : deviance;
    }

    boolean moreIsBetter = moreIsBetter(criterion);
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

      // Example: 18 scoring events, k=1
      // need to go back from idx 17 to idx 16
      // movingAvg[0] is based on scoring event index 16 <- reference
      // movingAvg[1] is based on scoring event index 17 <- first "new" score

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
          case RMSE:
            val = skj._rmse;
            break;
          case MAE:
            val = skj._mae;
            break;
          case RMSLE:
            val = skj._rmsle;
            break;
          case deviance:
            val = skj._mean_residual_deviance;
            break;
          case logloss:
            val = skj._logloss;
            break;
          case misclassification:
            val = skj._classError;
            break;
          case mean_per_class_error:
            val = skj._mean_per_class_error;
            break;
          case lift_top_group:
            val = skj._lift;
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
    if (verbose)
      Log.info("Windowed averages (window size " + k + ") of " + what + " " + (k+1) + " " + criterion.toString() + " metrics: " + Arrays.toString(movingAvg));

    if (lastBeforeK==0 && !moreIsBetter && !criterion.equals(deviance)) // deviance: less is better and can be negative
      return true;

    double ratio = bestInLastK / lastBeforeK;
    if (Double.isNaN(ratio)) return false;
    boolean improved = moreIsBetter ? ratio > 1+rel_improvement : ratio < 1-rel_improvement;

    if (verbose)
      Log.info("Checking convergence with " + criterion.toString() + " metric: " + lastBeforeK + " --> " + bestInLastK + (improved ? " (still improving)." : " (converged)."));
    return !improved;
  } // stopEarly

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
    return MathUtils.compare(_mean_residual_deviance, o._mean_residual_deviance, 1e-6, 1e-6)
            && MathUtils.compare(_mse, o._mse, 1e-6, 1e-6)
            && MathUtils.compare(_mae, o._mae, 1e-6, 1e-6)
            && MathUtils.compare(_rmsle, o._rmsle, 1e-6, 1e-6)
            && MathUtils.compare(_logloss, o._logloss, 1e-6, 1e-6)
            && MathUtils.compare(_classError, o._classError, 1e-6, 1e-6)
            && MathUtils.compare(_mean_per_class_error, o._mean_per_class_error, 1e-6, 1e-6)
            && MathUtils.compare(_lift, o._lift, 1e-6, 1e-6);
  }

  public static Comparator<ScoreKeeper> comparator(StoppingMetric criterion) {
    switch (criterion) {
      case AUC:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o2._AUC - o1._AUC); // moreIsBetter
          }
        };
      case RMSE:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._rmse - o2._rmse); // lessIsBetter
          }
        };
      case MAE:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._mae - o2._mae); // lessIsBetter
          }
        };
      case RMSLE:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._rmsle - o2._rmsle); // lessIsBetter
          }
        };
      case deviance:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._mean_residual_deviance - o2._mean_residual_deviance); // lessIsBetter
          }
        };
      case logloss:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._logloss - o2._logloss); // lessIsBetter
          }
        };
      case misclassification:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._classError - o2._classError); // lessIsBetter
          }
        };
      case mean_per_class_error:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o1._mean_per_class_error - o2._mean_per_class_error); // lessIsBetter
          }
        };
      case lift_top_group:
        return new Comparator<ScoreKeeper>() {
          @Override
          public int compare(ScoreKeeper o1, ScoreKeeper o2) {
            return (int)Math.signum(o2._lift - o1._lift); // moreIsBetter
          }
        };
      default:
        throw H2O.unimpl("Undefined stopping criterion.");
    } // switch
  } // comparator

  @Override
  public String toString() {
    return "ScoreKeeper{" +
        "  _mean_residual_deviance=" + _mean_residual_deviance +
        ", _rmse=" + _rmse +
            ",_mae=" + _mae +
            ",_rmsle=" + _rmsle +
        ", _logloss=" + _logloss +
        ", _AUC=" + _AUC +
        ", _classError=" + _classError +
        ", _mean_per_class_error=" + _mean_per_class_error +
        ", _hitratio=" + Arrays.toString(_hitratio) +
        ", _lift=" + _lift +
        '}';
  }
}
