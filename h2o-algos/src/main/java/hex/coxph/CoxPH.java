package hex.coxph;

import Jama.Matrix;
import hex.*;
import hex.DataInfo.Row;
import hex.DataInfo.TransformType;
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
  @Override public ModelCategory[] can_build() { return new ModelCategory[] { ModelCategory.Unknown, }; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Experimental; }
  @Override public boolean isSupervised() { return true; }

  public CoxPH(boolean startup_once) {
    super(new CoxPHModel.CoxPHParameters(), startup_once);
  }

  public CoxPH( CoxPHModel.CoxPHParameters parms ) { super(parms); init(false); }
  @Override protected CoxPHDriver trainModelImpl() { return new CoxPHDriver(); }

  /** Initialize the ModelBuilder, validating all arguments and preparing the
   *  training frame.  This call is expected to be overridden in the subclasses
   *  and each subclass will start with "super.init();".  This call is made
   *  by the front-end whenever the GUI is clicked, and needs to be fast;
   *  heavy-weight prep needs to wait for the trainModel() call.
  */
  @Override public void init(boolean expensive) {
    super.init(expensive);

    if (_parms._train != null) {
      if ((_parms._start_column != null) && !_parms.startVec().isInt())
        error("start_column", "start time must be null or of type integer");

      if (!_parms.stopVec().isInt())
        error("stop_column", "stop time must be of type integer");

      if (!_response.isInt() && (!_response.isCategorical()))
        error("response_column", "response/event column must be of type integer or factor");

      final int MAX_TIME_BINS = 10000;
      final long min_time = (_parms.startVec() == null) ? (long) _parms.stopVec().min() : (long) _parms.startVec().min() + 1;
      final int n_time = (int) (_parms.stopVec().max() - min_time + 1);
      if (n_time < 1)
        error("start_column", "start times must be strictly less than stop times");
      if (n_time > MAX_TIME_BINS)
        error("stop_column", "number of distinct stop times is " + n_time + "; maximum number allowed is " + MAX_TIME_BINS);
    }

    if (Double.isNaN(_parms._lre_min) || _parms._lre_min <= 0)
      error("lre_min", "lre_min must be a positive number");

    if (_parms._iter_max < 1)
      error("iter_max", "iter_max must be a positive integer");
  }

  public class CoxPHDriver extends Driver {

    private Frame reorderTrainFrameColumns() {
      Frame f = new Frame();

      Vec weightVec = null;
      Vec startVec = null;
      Vec stopVec = null;
      Vec eventVec = null;

      Vec[] vecs = train().vecs();
      String[] names = train().names();

      for (int i = 0; i < names.length; i++) {
        if (names[i].equals(_parms._weights_column))
          weightVec = vecs[i];
        else if (names[i].equals(_parms._start_column))
          startVec = vecs[i];
        else if (names[i].equals(_parms._stop_column))
          stopVec = vecs[i];
        else if (names[i].equals(_parms._response_column))
          eventVec = vecs[i];
        else
          f.add(names[i], vecs[i]);
      }

      if (weightVec != null)
        f.add(_parms._weights_column, weightVec);
      if (startVec != null)
        f.add(_parms._start_column, startVec);
      if (stopVec != null)
        f.add(_parms._stop_column, stopVec);
      if (eventVec != null)
        f.add(_parms._response_column, eventVec);

      return f;
    }

    protected void initStats(final CoxPHModel model, final DataInfo dinfo) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      o._n = p.stopVec().length();
      o.data_info = dinfo;
      final int n_offsets = _offset == null ? 0 : 1;
      final int n_coef    = o.data_info.fullN() - n_offsets;
      final String[] coefNames = o.data_info.coefNames();
      o._coef_names = new String[n_coef];
      System.arraycopy(coefNames, 0, o._coef_names, 0, n_coef);
      o._coef = MemoryManager.malloc8d(n_coef);
      o._exp_coef = MemoryManager.malloc8d(n_coef);
      o._exp_neg_coef = MemoryManager.malloc8d(n_coef);
      o._se_coef = MemoryManager.malloc8d(n_coef);
      o._z_coef = MemoryManager.malloc8d(n_coef);
      o.gradient     = MemoryManager.malloc8d(n_coef);
      o.hessian      = malloc2DArray(n_coef, n_coef);
      o._var_coef = malloc2DArray(n_coef, n_coef);
      o._x_mean_cat = MemoryManager.malloc8d(n_coef - (o.data_info._nums - n_offsets));
      o._x_mean_num = MemoryManager.malloc8d(o.data_info._nums - n_offsets);
      o._mean_offset = MemoryManager.malloc8d(n_offsets);
      o._offset_names = new String[n_offsets];
      System.arraycopy(coefNames, n_coef, o._offset_names, 0, n_offsets);

      o._min_time = p.startVec() == null ? (long) p.stopVec().min():
              (long) p.startVec().min() + 1;
      o._max_time = (long) p.stopVec().max();

      final int n_time = new VecUtils.CollectIntegerDomain().doAll(p.stopVec()).domain().length;
      o._time = MemoryManager.malloc8(n_time);
      o._n_risk = MemoryManager.malloc8d(n_time);
      o._n_event = MemoryManager.malloc8d(n_time);
      o._n_censor = MemoryManager.malloc8d(n_time);
      o._cumhaz_0 = MemoryManager.malloc8d(n_time);
      o._var_cumhaz_1 = MemoryManager.malloc8d(n_time);
      o._var_cumhaz_2 = malloc2DArray(n_time, n_coef);
    }

    protected void calcCounts(CoxPHModel model, final CoxPHTask coxMR) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      o._n_missing = o._n - coxMR.n;
      o._n = coxMR.n;
      for (int j = 0; j < o._x_mean_cat.length; j++)
        o._x_mean_cat[j] = coxMR.sumWeightedCatX[j] / coxMR.sumWeights;
      for (int j = 0; j < o._x_mean_num.length; j++)
        o._x_mean_num[j] = o.data_info._normSub[j] + coxMR.sumWeightedNumX[j] / coxMR.sumWeights;
      System.arraycopy(o.data_info._normSub, o._x_mean_num.length, o._mean_offset, 0, o._mean_offset.length);
      int nz = 0;
      for (int t = 0; t < coxMR.countEvents.length; ++t) {
        o._total_event += coxMR.countEvents[t];
        if (coxMR.sizeEvents[t] > 0 || coxMR.sizeCensored[t] > 0) {
          o._time[nz]     = o._min_time + t;
          o._n_risk[nz]   = coxMR.sizeRiskSet[t];
          o._n_event[nz]  = coxMR.sizeEvents[t];
          o._n_censor[nz] = coxMR.sizeCensored[t];
          nz++;
        }
      }
      if (p._start_column == null)
        for (int t = o._n_risk.length - 2; t >= 0; --t)
          o._n_risk[t] += o._n_risk[t + 1];
    }

    protected double calcLoglik(CoxPHModel model, final CoxPHTask coxMR) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o._coef.length;
      final int n_time = coxMR.sizeEvents.length;
      double newLoglik = 0;
      for (int j = 0; j < n_coef; ++j)
        o.gradient[j] = 0;
      for (int j = 0; j < n_coef; ++j)
        for (int k = 0; k < n_coef; ++k)
          o.hessian[j][k] = 0;

      switch (p._ties) {
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
          throw new IllegalArgumentException("_ties method must be either efron or breslow");
      }
      return newLoglik;
    }

    protected void calcModelStats(CoxPHModel model, final double[] newCoef, final double newLoglik) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o._coef.length;
      final Matrix inv_hessian = new Matrix(o.hessian).inverse();
      for (int j = 0; j < n_coef; ++j) {
        for (int k = 0; k <= j; ++k) {
          final double elem = -inv_hessian.get(j, k);
          o._var_coef[j][k] = elem;
          o._var_coef[k][j] = elem;
        }
      }
      for (int j = 0; j < n_coef; ++j) {
        o._coef[j]         = newCoef[j];
        o._exp_coef[j]     = Math.exp(o._coef[j]);
        o._exp_neg_coef[j] = Math.exp(- o._coef[j]);
        o._se_coef[j]      = Math.sqrt(o._var_coef[j][j]);
        o._z_coef[j]       = o._coef[j] / o._se_coef[j];
      }
      if (o._iter == 0) {
        o._null_loglik = newLoglik;
        o._maxrsq = 1 - Math.exp(2 * o._null_loglik / o._n);
        o._score_test = 0;
        for (int j = 0; j < n_coef; ++j) {
          double sum = 0;
          for (int k = 0; k < n_coef; ++k)
            sum += o._var_coef[j][k] * o.gradient[k];
          o._score_test += o.gradient[j] * sum;
        }
      }
      o._loglik = newLoglik;
      o._loglik_test = - 2 * (o._null_loglik - o._loglik);
      o._rsq = 1 - Math.exp(- o._loglik_test / o._n);
      o._wald_test = 0;
      for (int j = 0; j < n_coef; ++j) {
        double sum = 0;
        for (int k = 0; k < n_coef; ++k)
          sum -= o.hessian[j][k] * (o._coef[k] - p._init);
        o._wald_test += (o._coef[j] - p._init) * sum;
      }
    }

    protected void calcCumhaz_0(CoxPHModel model, final CoxPHTask coxMR) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o._coef.length;
      int nz = 0;
      switch (p._ties) {
        case efron:
          for (int t = 0; t < coxMR.sizeEvents.length; ++t) {
            final double sizeEvents_t   = coxMR.sizeEvents[t];
            final double sizeCensored_t = coxMR.sizeCensored[t];
            if (sizeEvents_t > 0 || sizeCensored_t > 0) {
              final long   countEvents_t   = coxMR.countEvents[t];
              final double sumRiskEvents_t = coxMR.sumRiskEvents[t];
              final double rcumsumRisk_t   = coxMR.rcumsumRisk[t];
              final double avgSize = sizeEvents_t / countEvents_t;
              o._cumhaz_0[nz]     = 0;
              o._var_cumhaz_1[nz] = 0;
              for (int j = 0; j < n_coef; ++j)
                o._var_cumhaz_2[nz][j] = 0;
              for (long e = 0; e < countEvents_t; ++e) {
                final double frac   = ((double) e) / ((double) countEvents_t);
                final double haz    = 1 / (rcumsumRisk_t - frac * sumRiskEvents_t);
                final double haz_sq = haz * haz;
                o._cumhaz_0[nz]     += avgSize * haz;
                o._var_cumhaz_1[nz] += avgSize * haz_sq;
                for (int j = 0; j < n_coef; ++j)
                  o._var_cumhaz_2[nz][j] +=
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
              o._cumhaz_0[nz]     = cumhaz_0_nz;
              o._var_cumhaz_1[nz] = sizeEvents_t / (rcumsumRisk_t * rcumsumRisk_t);
              for (int j = 0; j < n_coef; ++j)
                o._var_cumhaz_2[nz][j] = (coxMR.rcumsumXRisk[t][j] / rcumsumRisk_t) * cumhaz_0_nz;
              nz++;
            }
          }
          break;
        default:
          throw new IllegalArgumentException("_ties method must be either efron or breslow");
      }

      for (int t = 1; t < o._cumhaz_0.length; ++t) {
        o._cumhaz_0[t]     = o._cumhaz_0[t - 1]     + o._cumhaz_0[t];
        o._var_cumhaz_1[t] = o._var_cumhaz_1[t - 1] + o._var_cumhaz_1[t];
        for (int j = 0; j < n_coef; ++j)
          o._var_cumhaz_2[t][j] = o._var_cumhaz_2[t - 1][j] + o._var_cumhaz_2[t][j];
      }
    }

    @Override
    public void computeImpl() {
      CoxPHModel model = null;
      try {
        init(true);

        Frame f = reorderTrainFrameColumns();

        // The model to be built
        model = new CoxPHModel(_job._result, _parms, new CoxPHModel.CoxPHOutput(CoxPH.this));
        model.delete_and_lock(_job);

        int nResponses = _parms.startVec() == null ? 2 : 3;
        final DataInfo dinfo = new DataInfo(f, null, nResponses, false, DataInfo.TransformType.DEMEAN, TransformType.NONE, true, false, false, false, false, false);
        Scope.track_generic(dinfo);
        DKV.put(dinfo);

        initStats(model, dinfo);

        final int n_offsets = (_offset == null) ? 0 : 1;
        final int n_coef = dinfo.fullN() - n_offsets;
        final double[] step = MemoryManager.malloc8d(n_coef);
        final double[] oldCoef = MemoryManager.malloc8d(n_coef);
        final double[] newCoef = MemoryManager.malloc8d(n_coef);
        Arrays.fill(step, Double.NaN);
        Arrays.fill(oldCoef, Double.NaN);
        for (int j = 0; j < n_coef; ++j)
          newCoef[j] = model._parms._init;
        double oldLoglik = -Double.MAX_VALUE;
        final int n_time = (int) (model._output._max_time - model._output._min_time + 1);
        final boolean has_start_column = (model._parms.startVec() != null);
        final boolean has_weights_column = (_weights != null);
        for (int i = 0; i <= model._parms._iter_max; ++i) {
          model._output._iter = i;

          final CoxPHTask coxMR = new CoxPHTask(_job._key, dinfo, newCoef, model._output._min_time, n_time, n_offsets,
                  has_start_column, has_weights_column).doAll(dinfo._adaptedFrame);

          final double newLoglik = calcLoglik(model, coxMR);
          if (newLoglik > oldLoglik) {
            if (i == 0)
              calcCounts(model, coxMR);

            calcModelStats(model, newCoef, newLoglik);
            calcCumhaz_0(model, coxMR);

            if (newLoglik == 0)
              model._output._lre = -Math.log10(Math.abs(oldLoglik - newLoglik));
            else
              model._output._lre = -Math.log10(Math.abs((oldLoglik - newLoglik) / newLoglik));
            if (model._output._lre >= model._parms._lre_min)
              break;

            Arrays.fill(step, 0);
            for (int j = 0; j < n_coef; ++j)
              for (int k = 0; k < n_coef; ++k)
                step[j] -= model._output._var_coef[j][k] * model._output.gradient[k];
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

        model.update(_job);
      } finally {
        if (model != null) model.unlock(_job);
      }
    }

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

    CoxPHTask(Key<Job> jobKey, DataInfo dinfo, final double[] beta, final long min_time, final int n_time,
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
