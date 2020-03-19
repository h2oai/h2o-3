package hex;

import water.H2O;
import water.Iced;
import water.exceptions.H2OIllegalArgumentException;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MathUtils;

import java.util.Arrays;

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
  public double _pr_auc = Double.NaN;
  public double _pr_auc_xgboost = Double.NaN;
  public double _classError = Double.NaN;
  public double _mean_per_class_error = Double.NaN;
  public double _custom_metric = Double.NaN;
  public float[] _hitratio;
  public double _lift = Double.NaN; //Lift in top group
  public double _r2 = Double.NaN;
  public double _anomaly_score = Double.NaN;
  public double _anomaly_score_normalized = Double.NaN;

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
    return Double.isNaN(_mse) && Double.isNaN(_logloss) && Double.isNaN(_anomaly_score_normalized); // at least one of them should always be filled
  }

  public void fillFrom(ModelMetrics m) {
    if (m == null) return;
    _mse = m._MSE;
    _rmse = m.rmse();
    if (m instanceof ModelMetricsRegression) {
      _mean_residual_deviance = ((ModelMetricsRegression)m)._mean_residual_deviance;
      _mae = ((ModelMetricsRegression)m)._mean_absolute_error;
      _rmsle = ((ModelMetricsRegression)m)._root_mean_squared_log_error;
      _r2 = ((ModelMetricsRegression)m).r2();
    }
    if (m instanceof ModelMetricsBinomial) {
      _logloss = ((ModelMetricsBinomial)m)._logloss;
      _r2 = ((ModelMetricsBinomial)m).r2();
      if (((ModelMetricsBinomial)m)._auc != null) {
        _AUC = ((ModelMetricsBinomial) m)._auc._auc;
        _pr_auc = ((ModelMetricsBinomial) m)._auc.pr_auc();
        _pr_auc_xgboost = ((ModelMetricsBinomial) m)._auc.pr_auc_xgboost();
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
      _r2 = ((ModelMetricsMultinomial)m).r2();
    } else if (m instanceof ModelMetricsOrdinal) {
      _logloss = ((ModelMetricsOrdinal)m)._logloss;
      _classError = ((ModelMetricsOrdinal)m)._cm.err();
      _mean_per_class_error = ((ModelMetricsOrdinal)m).mean_per_class_error();
      _hitratio = ((ModelMetricsOrdinal)m)._hit_ratios;
      _r2 = ((ModelMetricsOrdinal)m).r2();
    } else if (m instanceof ScoreKeeperAware) {
      ((ScoreKeeperAware) m).fillTo(this);
    }
    if (m._custom_metric != null )
      _custom_metric =  m._custom_metric.value;
  }

  public interface IStoppingMetric {
    int direction();
    boolean isLowerBoundBy0();
    IConvergenceStrategy getConvergenceStrategy();
    double metricValue(ScoreKeeper sk);
  }
  
  public enum StoppingMetric implements IStoppingMetric {
    AUTO(ConvergenceStrategy.AUTO, false), 
    deviance(ConvergenceStrategy.LESS_IS_BETTER, false),
    logloss(ConvergenceStrategy.LESS_IS_BETTER, true),
    MSE(ConvergenceStrategy.LESS_IS_BETTER, true),
    RMSE(ConvergenceStrategy.LESS_IS_BETTER, true),
    MAE(ConvergenceStrategy.LESS_IS_BETTER, true),
    RMSLE(ConvergenceStrategy.LESS_IS_BETTER, true),
    AUC(ConvergenceStrategy.MORE_IS_BETTER, true),
    AUCPR(ConvergenceStrategy.MORE_IS_BETTER, true),
    lift_top_group(ConvergenceStrategy.MORE_IS_BETTER, false),
    misclassification(ConvergenceStrategy.LESS_IS_BETTER, true),
    mean_per_class_error(ConvergenceStrategy.LESS_IS_BETTER, true),
    anomaly_score(ConvergenceStrategy.NON_DIRECTIONAL, false),
    custom(ConvergenceStrategy.LESS_IS_BETTER, false),
    custom_increasing(ConvergenceStrategy.MORE_IS_BETTER, false),
    ;

    private final ConvergenceStrategy _convergence;
    private final boolean _lowerBoundBy0;

    StoppingMetric(ConvergenceStrategy convergence, boolean lowerBoundBy0) {
      _convergence = convergence;
      _lowerBoundBy0 = lowerBoundBy0;
    }

    public int direction() {
      return _convergence._direction;
    }

    public boolean isLowerBoundBy0() {
      return _lowerBoundBy0;
    }

    public ConvergenceStrategy getConvergenceStrategy() {
      return _convergence;
    }

    @Override
    public double metricValue(ScoreKeeper skj) {
      double val;
      switch (this) {
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
        case AUCPR:
          val = skj._pr_auc;
          break;
        case mean_per_class_error:
          val = skj._mean_per_class_error;
          break;
        case lift_top_group:
          val = skj._lift;
          break;
        case custom:
        case custom_increasing:
          val = skj._custom_metric;
          break;
        case anomaly_score:
          val = skj._anomaly_score_normalized;
          break;
        default:
          throw H2O.unimpl("Undefined stopping criterion.");
      }
      return val;
    }

  }

  public enum ProblemType {
    regression(StoppingMetric.deviance),
    classification(StoppingMetric.logloss),
    anomaly_detection(StoppingMetric.anomaly_score);

    private final StoppingMetric _defaultMetric;
    
    ProblemType(StoppingMetric defaultMetric) {
      _defaultMetric = defaultMetric;
    }

    public StoppingMetric defaultMetric() {
      return _defaultMetric;
    }

    public static ProblemType forSupervised(boolean isClassifier) {
      return isClassifier ? classification : regression;
    }
  }

  /** Based on the given array of ScoreKeeper and stopping criteria should we stop early? */
  public static boolean stopEarly(ScoreKeeper[] sk, int k, ProblemType type, IStoppingMetric criterion, double rel_improvement, String what, boolean verbose) {
    if (k == 0) return false;
    int len = sk.length - 1; //how many "full"/"conservative" scoring events we have (skip the first)
    if (len < 2*k) return false; //need at least k for SMA and another k to tell whether the model got better or not

    if (StoppingMetric.AUTO.equals(criterion)) {
      criterion = type.defaultMetric();
    }

    IConvergenceStrategy convergenceStrategy = criterion.getConvergenceStrategy();
    double movingAvg[] = new double[k+1]; //need one moving average value for the last k+1 scoring events
    double lastBeforeK = Double.MAX_VALUE;
    double minInLastK = Double.MAX_VALUE;
    double maxInLastK = -Double.MAX_VALUE;
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
        double val = criterion.metricValue(skj);
        movingAvg[i] += val;
      }
      movingAvg[i]/=k;
      if (Double.isNaN(movingAvg[i])) return false;
      if (i==0)
        lastBeforeK = movingAvg[i];
      else {
        minInLastK = Math.min(movingAvg[i], minInLastK);
        maxInLastK = Math.max(movingAvg[i], maxInLastK);
      }
    }
    assert(lastBeforeK != Double.MAX_VALUE);
    assert(maxInLastK != -Double.MAX_VALUE);
    assert(minInLastK != Double.MAX_VALUE);

    if (verbose)
      Log.info("Windowed averages (window size " + k + ") of " + what + " " + (k+1) + " " + criterion.toString() + " metrics: " + Arrays.toString(movingAvg));

    if (criterion.isLowerBoundBy0() && lastBeforeK == 0.0) {
      Log.info("Checking convergence with " + criterion.toString() + " metric: " + lastBeforeK + " (metric converged to its lower bound).");
      return true;
    }

    final double extremePoint = convergenceStrategy.extremePoint(lastBeforeK, minInLastK, maxInLastK);

    // zero-crossing could be for residual deviance or r^2 -> mark it not yet stopEarly, avoid division by 0 or weird relative improvements math below
    if (Math.signum(ArrayUtils.maxValue(movingAvg)) != Math.signum(ArrayUtils.minValue(movingAvg))) return false;
    if (Math.signum(extremePoint) != Math.signum(lastBeforeK)) 
      return false;

    boolean stopEarly = convergenceStrategy.stopEarly(lastBeforeK, minInLastK, maxInLastK, rel_improvement);

    if (verbose)
      Log.info("Checking convergence with " + criterion.toString() + " metric: " + lastBeforeK + " --> " + extremePoint + (stopEarly ? " (converged)." : " (still improving)."));
    return stopEarly;
  } // stopEarly

  interface IConvergenceStrategy {
    double extremePoint(double lastBeforeK, double minInLastK, double maxInLastK);
    boolean stopEarly(double lastBeforeK, double minInLastK, double maxInLastK, double rel_improvement);
  }
  
  enum ConvergenceStrategy implements IConvergenceStrategy {
    AUTO(0), // dummy - should never be actually used (meant to be assigned to AUTO metric) 
    MORE_IS_BETTER(1) {
      @Override
      public double extremePoint(double lastBeforeK, double minInLastK, double maxInLastK) {
        return maxInLastK;
      }
      @Override
      public boolean stopEarly(double lastBeforeK, double minInLastK, double maxInLastK, double rel_improvement) {
        double ratio = maxInLastK / lastBeforeK;
        if (Double.isNaN(ratio))
          return false;
        return ratio <= 1 + rel_improvement;
      }
    },
    LESS_IS_BETTER(-1) {
      @Override
      public double extremePoint(double lastBeforeK, double minInLastK, double maxInLastK) {
        return minInLastK;
      }
      @Override
      public boolean stopEarly(double lastBeforeK, double minInLastK, double maxInLastK, double rel_improvement) {
        double ratio = minInLastK / lastBeforeK;
        if (Double.isNaN(ratio))
          return false;
        return ratio >= 1 - rel_improvement;
      }
    },
    NON_DIRECTIONAL(0) {
      @Override
      public double extremePoint(double lastBeforeK, double minInLastK, double maxInLastK) {
        return Math.abs(lastBeforeK - minInLastK) > Math.abs(lastBeforeK - maxInLastK) ? minInLastK : maxInLastK;
      }
      @Override
      public boolean stopEarly(double lastBeforeK, double minInLastK, double maxInLastK, double rel_change) {
        double extreme = extremePoint(lastBeforeK, minInLastK, maxInLastK);
        double ratio = extreme / lastBeforeK;
        if (Double.isNaN(ratio))
          return false;
        return ratio >= 1 - rel_change && ratio <= 1 + rel_change;
      }
    };

    final int _direction;

    ConvergenceStrategy(int direction) {
      _direction = direction;
    }

    @Override
    public double extremePoint(double lastBeforeK, double minInLastK, double maxInLastK) {
      throw new IllegalStateException("Should overridden in Strategy implementation");
    }

    @Override
    public boolean stopEarly(double lastBeforeK, double minInLastK, double maxInLastK, double rel_improvement) {
      throw new IllegalStateException("Should overridden in Strategy implementation");
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
    return MathUtils.compare(_mean_residual_deviance, o._mean_residual_deviance, 1e-6, 1e-6)
            && MathUtils.compare(_mse, o._mse, 1e-6, 1e-6)
            && MathUtils.compare(_mae, o._mae, 1e-6, 1e-6)
            && MathUtils.compare(_rmsle, o._rmsle, 1e-6, 1e-6)
            && MathUtils.compare(_logloss, o._logloss, 1e-6, 1e-6)
            && MathUtils.compare(_classError, o._classError, 1e-6, 1e-6)
            && MathUtils.compare(_mean_per_class_error, o._mean_per_class_error, 1e-6, 1e-6)
            && MathUtils.compare(_r2, o._r2, 1e-6, 1e-6)
            && MathUtils.compare(_lift, o._lift, 1e-6, 1e-6);
  }

  @Override
  public String toString() {
    return "ScoreKeeper{" +
        "  _mean_residual_deviance=" + _mean_residual_deviance +
        ", _rmse=" + _rmse +
            ",_mae=" + _mae +
            ",_rmsle=" + _rmsle +
        ", _logloss=" + _logloss +
        ", _AUC=" + _AUC +
            ", _pr_auc="+_pr_auc+
            ", _pr_auc_xgboost="+_pr_auc_xgboost+
        ", _classError=" + _classError +
        ", _mean_per_class_error=" + _mean_per_class_error +
        ", _hitratio=" + Arrays.toString(_hitratio) +
        ", _lift=" + _lift +
        ", _anomaly_score_normalized=" + _anomaly_score_normalized +
        '}';
  }

  public interface ScoreKeeperAware {
    void fillTo(ScoreKeeper sk);
  }

}
