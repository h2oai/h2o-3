package hex.coxph;

import Jama.Matrix;
import hex.*;
import hex.DataInfo.Row;
import hex.DataInfo.TransformType;
import hex.schemas.ModelBuilderSchema;
import jsr166y.ForkJoinTask;
import jsr166y.RecursiveAction;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.util.*;
import java.util.Arrays;

/**
 * Deep Learning Neural Net implementation based on MRTask
 */
public class CoxPH extends ModelBuilder<CoxPHModel,CoxPHModel.CoxPHParameters,CoxPHModel.CoxPHOutput> {
  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[] {
      ModelCategory.Unknown,
    };
  }

  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; };

  public CoxPH( CoxPHModel.CoxPHParameters parms ) { super("CoxPHLearning",parms); init(false); }

  public ModelBuilderSchema schema() {
    H2O.unimpl();
    return null;
  //  return new CoxPHV2();
  }

  /** Start the Cox PH training Job on an F/J thread.
   * @param work*/
  @Override public Job<CoxPHModel> trainModelImpl(long work) {
    CoxPHDriver cd = new CoxPHDriver();
    cd.setModelBuilderTrain(_train);
    CoxPH cph = (CoxPH) start(cd, work);
    return cph;
  }

  @Override
  public long progressUnits() {
    return _parms.iter_max;
  }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
  */
  @Override public void init(boolean expensive) {
    super.init(expensive);

    if ((_parms.start_column != null) && !_parms.start_column.isInt())
      error("start_column", "start time must be null or of type integer");

    if (!_parms.stop_column.isInt())
      error("stop_column", "stop time must be of type integer");

    if (!_parms.event_column.isInt() && !_parms.event_column.isEnum())
      error("event_column", "event must be of type integer or factor");

    if (Double.isNaN(_parms.lre_min) || _parms.lre_min <= 0)
      error("lre_min", "lre_min must be a positive number");

    if (_parms.iter_max < 1)
      error("iter_max", "iter_max must be a positive integer");

    final int MAX_TIME_BINS = 10000;
    final long min_time = (_parms.start_column == null) ? (long) _parms.stop_column.min() : (long) _parms.start_column.min() + 1;
    final int n_time = (int) (_parms.stop_column.max() - min_time + 1);
    if (n_time < 1)
      error("start_column", "start times must be strictly less than stop times");
    if (n_time > MAX_TIME_BINS)
      error("stop_column", "number of distinct stop times is " + n_time + "; maximum number allowed is " + MAX_TIME_BINS);
  }

  public class CoxPHDriver extends H2O.H2OCountedCompleter<CoxPHDriver> {
    private Frame _modelBuilderTrain = null;

    public void setModelBuilderTrain(Frame v) {
      _modelBuilderTrain = v;
    }

    private void applyScoringFrameSideEffects() {
      final int offset_ncol = _parms.offset_columns == null ? 0 : _parms.offset_columns.length;
      if (offset_ncol == 0) {
        return;
      }

      int numCols = _modelBuilderTrain.numCols();
      String responseVecName = _modelBuilderTrain.names()[numCols-1];
      Vec responseVec = _modelBuilderTrain.remove(numCols-1);

      for (int i = 0; i < offset_ncol; i++) {
        Vec offsetVec = _parms.offset_columns[i];
        int idxInRawFrame = _train.find(offsetVec);
        if (idxInRawFrame < 0) {
          throw new RuntimeException("CoxPHDriver failed to find offsetVec");
        }

        String offsetVecName = _parms.train().names()[idxInRawFrame];
        _modelBuilderTrain.add(offsetVecName, offsetVec);
      }

      _modelBuilderTrain.add(responseVecName, responseVec);
    }

    private void applyTrainingFrameSideEffects() {
      int numCols = _modelBuilderTrain.numCols();
      String responseVecName = _modelBuilderTrain.names()[numCols-1];
      Vec responseVec = _modelBuilderTrain.remove(numCols-1);

      final boolean use_weights_column = (_parms.weights_column != null);
      final boolean use_start_column   = (_parms.start_column != null);

      if (use_weights_column) {
        Vec weightsVec = _parms.weights_column;
        int idxInRawFrame = _train.find(weightsVec);
        if (idxInRawFrame < 0) {
          throw new RuntimeException("CoxPHDriver failed to find weightVec");
        }

        String weightsVecName = _parms.train().names()[idxInRawFrame];
        _modelBuilderTrain.add(weightsVecName, weightsVec);
      }

      if (use_start_column) {
        Vec startVec = _parms.start_column;
        int idxInRawFrame = _train.find(startVec);
        if (idxInRawFrame < 0) {
          throw new RuntimeException("CoxPHDriver failed to find startVec");
        }

        String startVecName = _parms.train().names()[idxInRawFrame];
        _modelBuilderTrain.add(startVecName, startVec);
      }

      {
        Vec stopVec = _parms.stop_column;
        int idxInRawFrame = _train.find(stopVec);
        if (idxInRawFrame < 0) {
          throw new RuntimeException("CoxPHDriver failed to find stopVec");
        }

        String stopVecName = _parms.train().names()[idxInRawFrame];
        _modelBuilderTrain.add(stopVecName, stopVec);
      }

      _modelBuilderTrain.add(responseVecName, responseVec);
    }

    protected void initStats(final CoxPHModel model, final DataInfo dinfo) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      o.n = p.stop_column.length();
      o.data_info = dinfo;
      final int n_offsets = (p.offset_columns == null) ? 0 : p.offset_columns.length;
      final int n_coef    = o.data_info.fullN() - n_offsets;
      final String[] coefNames = o.data_info.coefNames();
      o.coef_names   = new String[n_coef];
      System.arraycopy(coefNames, 0, o.coef_names, 0, n_coef);
      o.coef         = MemoryManager.malloc8d(n_coef);
      o.exp_coef     = MemoryManager.malloc8d(n_coef);
      o.exp_neg_coef = MemoryManager.malloc8d(n_coef);
      o.se_coef      = MemoryManager.malloc8d(n_coef);
      o.z_coef       = MemoryManager.malloc8d(n_coef);
      o.gradient     = MemoryManager.malloc8d(n_coef);
      o.hessian      = malloc2DArray(n_coef, n_coef);
      o.var_coef     = malloc2DArray(n_coef, n_coef);
      o.x_mean_cat   = MemoryManager.malloc8d(n_coef - (o.data_info._nums - n_offsets));
      o.x_mean_num   = MemoryManager.malloc8d(o.data_info._nums - n_offsets);
      o.mean_offset  = MemoryManager.malloc8d(n_offsets);
      o.offset_names = new String[n_offsets];
      System.arraycopy(coefNames, n_coef, o.offset_names, 0, n_offsets);

      final Vec start_column = p.start_column;
      final Vec stop_column  = p.stop_column;
      o.min_time = p.start_column == null ? (long) stop_column.min():
              (long) start_column.min() + 1;
      o.max_time = (long) stop_column.max();

      final int n_time = new Vec.CollectDomain().doAll(stop_column).domain().length;
      o.time         = MemoryManager.malloc8(n_time);
      o.n_risk       = MemoryManager.malloc8d(n_time);
      o.n_event      = MemoryManager.malloc8d(n_time);
      o.n_censor     = MemoryManager.malloc8d(n_time);
      o.cumhaz_0     = MemoryManager.malloc8d(n_time);
      o.var_cumhaz_1 = MemoryManager.malloc8d(n_time);
      o.var_cumhaz_2 = malloc2DArray(n_time, n_coef);
    }

    protected void calcCounts(CoxPHModel model, final CoxPHTask coxMR) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      o.n_missing = o.n - coxMR.n;
      o.n         = coxMR.n;
      for (int j = 0; j < o.x_mean_cat.length; j++)
        o.x_mean_cat[j] = coxMR.sumWeightedCatX[j] / coxMR.sumWeights;
      for (int j = 0; j < o.x_mean_num.length; j++)
        o.x_mean_num[j] = coxMR.dinfo()._normSub[j] + coxMR.sumWeightedNumX[j] / coxMR.sumWeights;
      System.arraycopy(coxMR.dinfo()._normSub, o.x_mean_num.length, o.mean_offset, 0, o.mean_offset.length);
      int nz = 0;
      for (int t = 0; t < coxMR.countEvents.length; ++t) {
        o.total_event += coxMR.countEvents[t];
        if (coxMR.sizeEvents[t] > 0 || coxMR.sizeCensored[t] > 0) {
          o.time[nz]     = o.min_time + t;
          o.n_risk[nz]   = coxMR.sizeRiskSet[t];
          o.n_event[nz]  = coxMR.sizeEvents[t];
          o.n_censor[nz] = coxMR.sizeCensored[t];
          nz++;
        }
      }
      if (p.start_column == null)
        for (int t = o.n_risk.length - 2; t >= 0; --t)
          o.n_risk[t] += o.n_risk[t + 1];
    }

    protected double calcLoglik(CoxPHModel model, final CoxPHTask coxMR) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o.coef.length;
      final int n_time = coxMR.sizeEvents.length;
      double newLoglik = 0;
      for (int j = 0; j < n_coef; ++j)
        o.gradient[j] = 0;
      for (int j = 0; j < n_coef; ++j)
        for (int k = 0; k < n_coef; ++k)
          o.hessian[j][k] = 0;

      switch (p.ties) {
        case efron:
          final double[]   newLoglik_t = MemoryManager.malloc8d(n_time);
          final double[][]  gradient_t = malloc2DArray(n_time, n_coef);
          final double[][][] hessian_t = malloc3DArray(n_time, n_coef, n_coef);
          ForkJoinTask[] fjts = new ForkJoinTask[n_time];
          for (int t = n_time - 1; t >= 0; --t) {
            final int _t = t;
            fjts[t] = new RecursiveAction() {
              @Override protected void compute() {
                final double sizeEvents_t = coxMR.sizeEvents[_t];
                if (sizeEvents_t > 0) {
                  final long   countEvents_t      = coxMR.countEvents[_t];
                  final double sumLogRiskEvents_t = coxMR.sumLogRiskEvents[_t];
                  final double sumRiskEvents_t    = coxMR.sumRiskEvents[_t];
                  final double rcumsumRisk_t      = coxMR.rcumsumRisk[_t];
                  final double avgSize            = sizeEvents_t / countEvents_t;
                  newLoglik_t[_t] = sumLogRiskEvents_t;
                  System.arraycopy(coxMR.sumXEvents[_t], 0, gradient_t[_t], 0, n_coef);
                  for (long e = 0; e < countEvents_t; ++e) {
                    final double frac = ((double) e) / ((double) countEvents_t);
                    final double term = rcumsumRisk_t - frac * sumRiskEvents_t;
                    newLoglik_t[_t] -= avgSize * Math.log(term);
                    for (int j = 0; j < n_coef; ++j) {
                      final double djTerm    = coxMR.rcumsumXRisk[_t][j] - frac * coxMR.sumXRiskEvents[_t][j];
                      final double djLogTerm = djTerm / term;
                      gradient_t[_t][j] -= avgSize * djLogTerm;
                      for (int k = 0; k < n_coef; ++k) {
                        final double dkTerm  = coxMR.rcumsumXRisk[_t][k] - frac * coxMR.sumXRiskEvents[_t][k];
                        final double djkTerm = coxMR.rcumsumXXRisk[_t][j][k] - frac * coxMR.sumXXRiskEvents[_t][j][k];
                        hessian_t[_t][j][k] -= avgSize * (djkTerm / term - (djLogTerm * (dkTerm / term)));
                      }
                    }
                  }
                }
              }
            };
          }
          ForkJoinTask.invokeAll(fjts);

          for (int t = 0; t < n_time; ++t)
            newLoglik += newLoglik_t[t];

          for (int t = 0; t < n_time; ++t)
            for (int j = 0; j < n_coef; ++j)
              o.gradient[j] += gradient_t[t][j];

          for (int t = 0; t < n_time; ++t)
            for (int j = 0; j < n_coef; ++j)
              for (int k = 0; k < n_coef; ++k)
                o.hessian[j][k] += hessian_t[t][j][k];
          break;
        case breslow:
          for (int t = n_time - 1; t >= 0; --t) {
            final double sizeEvents_t = coxMR.sizeEvents[t];
            if (sizeEvents_t > 0) {
              final double sumLogRiskEvents_t = coxMR.sumLogRiskEvents[t];
              final double rcumsumRisk_t      = coxMR.rcumsumRisk[t];
              newLoglik += sumLogRiskEvents_t;
              newLoglik -= sizeEvents_t * Math.log(rcumsumRisk_t);
              for (int j = 0; j < n_coef; ++j) {
                final double dlogTerm = coxMR.rcumsumXRisk[t][j] / rcumsumRisk_t;
                o.gradient[j] += coxMR.sumXEvents[t][j];
                o.gradient[j] -= sizeEvents_t * dlogTerm;
                for (int k = 0; k < n_coef; ++k)
                  o.hessian[j][k] -= sizeEvents_t *
                          (((coxMR.rcumsumXXRisk[t][j][k] / rcumsumRisk_t) -
                                  (dlogTerm * (coxMR.rcumsumXRisk[t][k] / rcumsumRisk_t))));
              }
            }
          }
          break;
        default:
          throw new IllegalArgumentException("ties method must be either efron or breslow");
      }
      return newLoglik;
    }

    protected void calcModelStats(CoxPHModel model, final double[] newCoef, final double newLoglik) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o.coef.length;
      final Matrix inv_hessian = new Matrix(o.hessian).inverse();
      for (int j = 0; j < n_coef; ++j) {
        for (int k = 0; k <= j; ++k) {
          final double elem = -inv_hessian.get(j, k);
          o.var_coef[j][k] = elem;
          o.var_coef[k][j] = elem;
        }
      }
      for (int j = 0; j < n_coef; ++j) {
        o.coef[j]         = newCoef[j];
        o.exp_coef[j]     = Math.exp(o.coef[j]);
        o.exp_neg_coef[j] = Math.exp(- o.coef[j]);
        o.se_coef[j]      = Math.sqrt(o.var_coef[j][j]);
        o.z_coef[j]       = o.coef[j] / o.se_coef[j];
      }
      if (o.iter == 0) {
        o.null_loglik = newLoglik;
        o.maxrsq      = 1 - Math.exp(2 * o.null_loglik / o.n);
        o.score_test  = 0;
        for (int j = 0; j < n_coef; ++j) {
          double sum = 0;
          for (int k = 0; k < n_coef; ++k)
            sum += o.var_coef[j][k] * o.gradient[k];
          o.score_test += o.gradient[j] * sum;
        }
      }
      o.loglik      = newLoglik;
      o.loglik_test = - 2 * (o.null_loglik - o.loglik);
      o.rsq         = 1 - Math.exp(- o.loglik_test / o.n);
      o.wald_test   = 0;
      for (int j = 0; j < n_coef; ++j) {
        double sum = 0;
        for (int k = 0; k < n_coef; ++k)
          sum -= o.hessian[j][k] * (o.coef[k] - p.init);
        o.wald_test += (o.coef[j] - p.init) * sum;
      }
    }

    protected void calcCumhaz_0(CoxPHModel model, final CoxPHTask coxMR) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o.coef.length;
      int nz = 0;
      switch (p.ties) {
        case efron:
          for (int t = 0; t < coxMR.sizeEvents.length; ++t) {
            final double sizeEvents_t   = coxMR.sizeEvents[t];
            final double sizeCensored_t = coxMR.sizeCensored[t];
            if (sizeEvents_t > 0 || sizeCensored_t > 0) {
              final long   countEvents_t   = coxMR.countEvents[t];
              final double sumRiskEvents_t = coxMR.sumRiskEvents[t];
              final double rcumsumRisk_t   = coxMR.rcumsumRisk[t];
              final double avgSize = sizeEvents_t / countEvents_t;
              o.cumhaz_0[nz]     = 0;
              o.var_cumhaz_1[nz] = 0;
              for (int j = 0; j < n_coef; ++j)
                o.var_cumhaz_2[nz][j] = 0;
              for (long e = 0; e < countEvents_t; ++e) {
                final double frac   = ((double) e) / ((double) countEvents_t);
                final double haz    = 1 / (rcumsumRisk_t - frac * sumRiskEvents_t);
                final double haz_sq = haz * haz;
                o.cumhaz_0[nz]     += avgSize * haz;
                o.var_cumhaz_1[nz] += avgSize * haz_sq;
                for (int j = 0; j < n_coef; ++j)
                  o.var_cumhaz_2[nz][j] +=
                          avgSize * ((coxMR.rcumsumXRisk[t][j] - frac * coxMR.sumXRiskEvents[t][j]) * haz_sq);
              }
              nz++;
            }
          }
          break;
        case breslow:
          for (int t = 0; t < coxMR.sizeEvents.length; ++t) {
            final double sizeEvents_t   = coxMR.sizeEvents[t];
            final double sizeCensored_t = coxMR.sizeCensored[t];
            if (sizeEvents_t > 0 || sizeCensored_t > 0) {
              final double rcumsumRisk_t = coxMR.rcumsumRisk[t];
              final double cumhaz_0_nz   = sizeEvents_t / rcumsumRisk_t;
              o.cumhaz_0[nz]     = cumhaz_0_nz;
              o.var_cumhaz_1[nz] = sizeEvents_t / (rcumsumRisk_t * rcumsumRisk_t);
              for (int j = 0; j < n_coef; ++j)
                o.var_cumhaz_2[nz][j] = (coxMR.rcumsumXRisk[t][j] / rcumsumRisk_t) * cumhaz_0_nz;
              nz++;
            }
          }
          break;
        default:
          throw new IllegalArgumentException("ties method must be either efron or breslow");
      }

      for (int t = 1; t < o.cumhaz_0.length; ++t) {
        o.cumhaz_0[t]     = o.cumhaz_0[t - 1]     + o.cumhaz_0[t];
        o.var_cumhaz_1[t] = o.var_cumhaz_1[t - 1] + o.var_cumhaz_1[t];
        for (int j = 0; j < n_coef; ++j)
          o.var_cumhaz_2[t][j] = o.var_cumhaz_2[t - 1][j] + o.var_cumhaz_2[t][j];
      }
    }

    @Override protected void compute2() {
      CoxPHModel model = null;
      try {
        Scope.enter();
        _parms.read_lock_frames(CoxPH.this);
        init(true);

        applyScoringFrameSideEffects();

        // The model to be built
        model = new CoxPHModel(dest(), _parms, new CoxPHModel.CoxPHOutput(CoxPH.this));
        model.delete_and_lock(_key);

        applyTrainingFrameSideEffects();

        int nResponses = 1;
        boolean useAllFactorLevels = false;
        final DataInfo dinfo = new DataInfo(Key.make(), _modelBuilderTrain, null, nResponses, useAllFactorLevels, DataInfo.TransformType.DEMEAN, TransformType.NONE, true, false, false, false, false);
        initStats(model, dinfo);

        final int n_offsets    = (model._parms.offset_columns == null) ? 0 : model._parms.offset_columns.length;
        final int n_coef       = dinfo.fullN() - n_offsets;
        final double[] step    = MemoryManager.malloc8d(n_coef);
        final double[] oldCoef = MemoryManager.malloc8d(n_coef);
        final double[] newCoef = MemoryManager.malloc8d(n_coef);
        Arrays.fill(step,    Double.NaN);
        Arrays.fill(oldCoef, Double.NaN);
        for (int j = 0; j < n_coef; ++j)
          newCoef[j] = model._parms.init;
        double oldLoglik = - Double.MAX_VALUE;
        final int n_time = (int) (model._output.max_time - model._output.min_time + 1);
        final boolean has_start_column   = (model._parms.start_column != null);
        final boolean has_weights_column = (model._parms.weights_column != null);
        for (int i = 0; i <= model._parms.iter_max; ++i) {
          model._output.iter = i;

          final CoxPHTask coxMR = new CoxPHTask(self(), dinfo, newCoef, model._output.min_time, n_time, n_offsets,
                  has_start_column, has_weights_column).doAll(dinfo._adaptedFrame);

          final double newLoglik = calcLoglik(model, coxMR);
          if (newLoglik > oldLoglik) {
            if (i == 0)
              calcCounts(model, coxMR);

            calcModelStats(model, newCoef, newLoglik);
            calcCumhaz_0(model, coxMR);

            if (newLoglik == 0)
              model._output.lre = - Math.log10(Math.abs(oldLoglik - newLoglik));
            else
              model._output.lre = - Math.log10(Math.abs((oldLoglik - newLoglik) / newLoglik));
            if (model._output.lre >= model._parms.lre_min)
              break;

            Arrays.fill(step, 0);
            for (int j = 0; j < n_coef; ++j)
              for (int k = 0; k < n_coef; ++k)
                step[j] -= model._output.var_coef[j][k] * model._output.gradient[k];
            for (int j = 0; j < n_coef; ++j)
              if (Double.isNaN(step[j]) || Double.isInfinite(step[j]))
                break;

            oldLoglik = newLoglik;
            System.arraycopy(newCoef, 0, oldCoef, 0, oldCoef.length);
          } else {
            for (int j = 0; j < n_coef; ++j)
              step[j] /= 2;
          }

          for (int j = 0; j < n_coef; ++j)
            newCoef[j] = oldCoef[j] - step[j];
        }

        model.update(_key);
      } catch( Throwable t ) {
        Job thisJob = DKV.getGet(_key);
        if (thisJob._state == JobState.CANCELLED) {
          Log.info("Job cancelled by user.");
        } else {
          t.printStackTrace();
          failed(t);
          throw t;
        }
      } finally {
        _parms.read_unlock_frames(CoxPH.this);
        Scope.exit();
        done();                 // Job done!
      }
      tryComplete();
    }

    Key self() { return _key; }

//  /**
//   * Report the relative progress of building a Deep Learning model (measured by how many epochs are done)
//   * @return floating point number between 0 and 1
//   */
//  @Override public float progress(){
//    if(UKV.get(dest()) == null)return 0;
//    DeepLearningModel m = UKV.get(dest());
//    if (m != null && m.model_info()!=null ) {
//      final float p = (float) Math.min(1, (m.epoch_counter / m.model_info().get_params().epochs));
//      return cv_progress(p);
//    }
//    return 0;
//  }



  }






  private static double[][] malloc2DArray(final int d1, final int d2) {
    final double[][] array = new double[d1][];
    for (int j = 0; j < d1; ++j)
      array[j] = MemoryManager.malloc8d(d2);
    return array;
  }

  private static double[][][] malloc3DArray(final int d1, final int d2, final int d3) {
    final double[][][] array = new double[d1][d2][];
    for (int j = 0; j < d1; ++j)
      for (int k = 0; k < d2; ++k)
        array[j][k] = MemoryManager.malloc8d(d3);
    return array;
  }

  protected static class CoxPHTask extends FrameTask<CoxPHTask> {
    private final double[] _beta;
    private final int      _n_time;
    private final long     _min_time;
    private final int      _n_offsets;
    private final boolean  _has_start_column;
    private final boolean  _has_weights_column;

    protected long         n;
    protected long         n_missing;
    protected double       sumWeights;
    protected double[]     sumWeightedCatX;
    protected double[]     sumWeightedNumX;
    protected double[]     sizeRiskSet;
    protected double[]     sizeCensored;
    protected double[]     sizeEvents;
    protected long[]       countEvents;
    protected double[][]   sumXEvents;
    protected double[]     sumRiskEvents;
    protected double[][]   sumXRiskEvents;
    protected double[][][] sumXXRiskEvents;
    protected double[]     sumLogRiskEvents;
    protected double[]     rcumsumRisk;
    protected double[][]   rcumsumXRisk;
    protected double[][][] rcumsumXXRisk;

    CoxPHTask(Key jobKey, DataInfo dinfo, final double[] beta, final long min_time, final int n_time,
              final int n_offsets, final boolean has_start_column, final boolean has_weights_column) {
      super(jobKey, dinfo);
      _beta               = beta;
      _n_time             = n_time;
      _min_time           = min_time;
      _n_offsets          = n_offsets;
      _has_start_column   = has_start_column;
      _has_weights_column = has_weights_column;
    }

    @Override
    protected boolean chunkInit(){
      final int n_coef = _beta.length;
      sumWeightedCatX  = MemoryManager.malloc8d(n_coef - (_dinfo._nums - _n_offsets));
      sumWeightedNumX  = MemoryManager.malloc8d(_dinfo._nums);
      sizeRiskSet      = MemoryManager.malloc8d(_n_time);
      sizeCensored     = MemoryManager.malloc8d(_n_time);
      sizeEvents       = MemoryManager.malloc8d(_n_time);
      countEvents      = MemoryManager.malloc8(_n_time);
      sumRiskEvents    = MemoryManager.malloc8d(_n_time);
      sumLogRiskEvents = MemoryManager.malloc8d(_n_time);
      rcumsumRisk      = MemoryManager.malloc8d(_n_time);
      sumXEvents       = malloc2DArray(_n_time, n_coef);
      sumXRiskEvents   = malloc2DArray(_n_time, n_coef);
      rcumsumXRisk     = malloc2DArray(_n_time, n_coef);
      sumXXRiskEvents  = malloc3DArray(_n_time, n_coef, n_coef);
      rcumsumXXRisk    = malloc3DArray(_n_time, n_coef, n_coef);
      return true;
    }

    @Override
    protected void processRow(long gid, Row row) {
      n++;
      double [] response = row.response;
      int ncats = row.nBins;
      int [] cats = row.numIds;
      double [] nums = row.numVals;
      final double weight = _has_weights_column ? response[0] : 1.0;
      if (weight <= 0)
        throw new IllegalArgumentException("weights must be positive values");
      final long event = (long) response[response.length - 1];
      final int t1 = _has_start_column ? (int) (((long) response[response.length - 3] + 1) - _min_time) : -1;
      final int t2 = (int) (((long) response[response.length - 2]) - _min_time);
      if (t1 > t2)
        throw new IllegalArgumentException("start times must be strictly less than stop times");
      final int numStart = _dinfo.numStart();
      sumWeights += weight;
      for (int j = 0; j < ncats; ++j)
        sumWeightedCatX[cats[j]] += weight;
      for (int j = 0; j < nums.length; ++j)
        sumWeightedNumX[j] += weight * nums[j];
      double logRisk = 0;
      for (int j = 0; j < ncats; ++j)
        logRisk += _beta[cats[j]];
      for (int j = 0; j < nums.length - _n_offsets; ++j)
        logRisk += nums[j] * _beta[numStart + j];
      for (int j = nums.length - _n_offsets; j < nums.length; ++j)
        logRisk += nums[j];
      final double risk = weight * Math.exp(logRisk);
      logRisk *= weight;
      if (event > 0) {
        countEvents[t2]++;
        sizeEvents[t2]       += weight;
        sumLogRiskEvents[t2] += logRisk;
        sumRiskEvents[t2]    += risk;
      } else
        sizeCensored[t2] += weight;
      if (_has_start_column) {
        for (int t = t1; t <= t2; ++t)
          sizeRiskSet[t] += weight;
        for (int t = t1; t <= t2; ++t)
          rcumsumRisk[t] += risk;
      } else {
        sizeRiskSet[t2]  += weight;
        rcumsumRisk[t2]  += risk;
      }

      final int ntotal = ncats + (nums.length - _n_offsets);
      final int numStartIter = numStart - ncats;
      for (int jit = 0; jit < ntotal; ++jit) {
        final boolean jIsCat = jit < ncats;
        final int j          = jIsCat ? cats[jit] : numStartIter + jit;
        final double x1      = jIsCat ? 1.0 : nums[jit - ncats];
        final double xRisk   = x1 * risk;
        if (event > 0) {
          sumXEvents[t2][j]     += weight * x1;
          sumXRiskEvents[t2][j] += xRisk;
        }
        if (_has_start_column) {
          for (int t = t1; t <= t2; ++t)
            rcumsumXRisk[t][j]  += xRisk;
        } else {
          rcumsumXRisk[t2][j]   += xRisk;
        }
        for (int kit = 0; kit < ntotal; ++kit) {
          final boolean kIsCat = kit < ncats;
          final int k          = kIsCat ? cats[kit] : numStartIter + kit;
          final double x2      = kIsCat ? 1.0 : nums[kit - ncats];
          final double xxRisk  = x2 * xRisk;
          if (event > 0)
            sumXXRiskEvents[t2][j][k] += xxRisk;
          if (_has_start_column) {
            for (int t = t1; t <= t2; ++t)
              rcumsumXXRisk[t][j][k]  += xxRisk;
          } else {
            rcumsumXXRisk[t2][j][k]   += xxRisk;
          }
        }
      }
    }

    @Override
    public void reduce(CoxPHTask that) {
      n += that.n;
      sumWeights += that.sumWeights;
      ArrayUtils.add(sumWeightedCatX, that.sumWeightedCatX);
      ArrayUtils.add(sumWeightedNumX,  that.sumWeightedNumX);
      ArrayUtils.add(sizeRiskSet,      that.sizeRiskSet);
      ArrayUtils.add(sizeCensored,     that.sizeCensored);
      ArrayUtils.add(sizeEvents,       that.sizeEvents);
      ArrayUtils.add(countEvents,      that.countEvents);
      ArrayUtils.add(sumXEvents,       that.sumXEvents);
      ArrayUtils.add(sumRiskEvents,    that.sumRiskEvents);
      ArrayUtils.add(sumXRiskEvents,   that.sumXRiskEvents);
      ArrayUtils.add(sumXXRiskEvents,  that.sumXXRiskEvents);
      ArrayUtils.add(sumLogRiskEvents, that.sumLogRiskEvents);
      ArrayUtils.add(rcumsumRisk,      that.rcumsumRisk);
      ArrayUtils.add(rcumsumXRisk,     that.rcumsumXRisk);
      ArrayUtils.add(rcumsumXXRisk,    that.rcumsumXXRisk);
    }

    @Override
    protected void postGlobal() {
      if (!_has_start_column) {
        for (int t = rcumsumRisk.length - 2; t >= 0; --t)
          rcumsumRisk[t] += rcumsumRisk[t + 1];

        for (int t = rcumsumXRisk.length - 2; t >= 0; --t)
          for (int j = 0; j < rcumsumXRisk[t].length; ++j)
            rcumsumXRisk[t][j] += rcumsumXRisk[t + 1][j];

        for (int t = rcumsumXXRisk.length - 2; t >= 0; --t)
          for (int j = 0; j < rcumsumXXRisk[t].length; ++j)
            for (int k = 0; k < rcumsumXXRisk[t][j].length; ++k)
              rcumsumXXRisk[t][j][k] += rcumsumXXRisk[t + 1][j][k];
      }
    }
  }
}
