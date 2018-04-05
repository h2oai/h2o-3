package hex.coxph;

import Jama.Matrix;
import hex.*;
import hex.DataInfo.Row;
import hex.DataInfo.TransformType;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.rapids.ast.prims.mungers.AstGroup;
import water.util.*;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * Cox Proportional Hazards Model
 */
public class CoxPH extends ModelBuilder<CoxPHModel,CoxPHModel.CoxPHParameters,CoxPHModel.CoxPHOutput> {

  private static final int MAX_TIME_BINS = 10000;

  @Override public ModelCategory[] can_build() { return new ModelCategory[] { ModelCategory.CoxPH }; }
  @Override public BuilderVisibility builderVisibility() { return BuilderVisibility.Stable; }
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

    if (_parms._train != null && _parms.train() == null) {
      error("train", "Invalid training frame (Frame key = " + _parms._train + " not found)");
    }

    if (_parms._train != null && _parms.train() != null) {
      if (_parms._start_column != null) {
        if (_parms.startVec().isNumeric()) {
          if (expensive) {
            try {
              CollectTimes.collect(_parms.stopVec());
            } catch (CollectTimesException e) {
              error("stop_column", e.getMessage());
            }
          }
        } else {
          error("start_column", "start time must be undefined or of type numeric");
        }
      }

      if ((_parms._stop_column != null) && ! _parms.stopVec().isNumeric())
        error("stop_column", "stop time must be of type numeric");

      if ((_parms._response_column != null) && ! _response.isInt() && (! _response.isCategorical()))
        error("response_column", "response/event column must be of type integer or factor");

      if (_parms._start_column != null && _parms._stop_column != null) {
        if (_parms.startVec().min() >= _parms.stopVec().max())
          error("start_column", "start times must be strictly less than stop times");
      }

      if (_parms.isStratified()) {
        for (String col : _parms._stratify_by) {
          Vec v = _parms.train().vec(col);
          if (v == null || v.get_type() != Vec.T_CAT)
            error("stratify_by", "non-categorical column '" + col + "' cannot be used for stratification");
          if (_parms._interactions != null) {
            for (String inter : _parms._interactions) {
              if (col.equals(inter)) {
                // Makes implementation simpler and should not have an actual impact anyway
                error("stratify_by", "stratification column '" + col + "' cannot be used in an implicit interaction. " +
                        "Use explicit (pair-wise) interactions instead");
                break;
              }
            }
          }
        }
      }
    }

    if (Double.isNaN(_parms._lre_min) || _parms._lre_min <= 0)
      error("lre_min", "lre_min must be a positive number");

    if (_parms._iter_max < 1)
      error("iter_max", "iter_max must be a positive integer");
  }

  static class StrataTask extends MRTask<StrataTask> {
    private IcedHashMap<AstGroup.G, IcedInt> _strataMap;

    private StrataTask(IcedHashMap<AstGroup.G, IcedInt> strata) { _strataMap = strata; }

    @Override
    public void map(Chunk[] cs, NewChunk nc) {
      AstGroup.G g = new AstGroup.G(cs.length, null);
      for (int i = 0; i < cs[0].len(); i++) {
        g.fill(i, cs);
        IcedInt strataId = _strataMap.get(g);
        if (strataId == null)
          nc.addNA();
        else
          nc.addNum(strataId._val);
      }
    }

    static Vec makeStrataVec(Frame f, String[] stratifyBy, IcedHashMap<AstGroup.G, IcedInt> mapping) {
      final Frame sf = f.subframe(stratifyBy);
      return new StrataTask(mapping).doAll(Vec.T_NUM, sf).outputFrame().anyVec();
    }

    static void setupStrataMapping(Frame f, String[] stratifyBy, IcedHashMap<AstGroup.G, IcedInt> outMapping) {
      final Frame sf = f.subframe(stratifyBy);
      int[] idxs = MemoryManager.malloc4(stratifyBy.length);
      for (int i = 0; i < idxs.length; i++)
        idxs[i] = i;
      IcedHashMap<AstGroup.G, String> groups = AstGroup.doGroups(sf, idxs, AstGroup.aggNRows());
      groups: for (AstGroup.G g : groups.keySet()) {
        for (double val : g._gs)
          if (Double.isNaN(val))
            continue groups;
        outMapping.put(g, new IcedInt(outMapping.size()));
      }
    }

  }

  public class CoxPHDriver extends Driver {

    private Frame reorderTrainFrameColumns(IcedHashMap<AstGroup.G, IcedInt> outStrataMap) {
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

      Vec strataVec = null;
      if (_parms.isStratified()) {
        StrataTask.setupStrataMapping(f, _parms._stratify_by, outStrataMap);
        strataVec = StrataTask.makeStrataVec(f, _parms._stratify_by, outStrataMap);
        if (_parms.interactionSpec() == null) {
          // no interactions => we can drop the columns earlier
          f.remove(_parms._stratify_by);
        }
      }

      if (weightVec != null)
        f.add(_parms._weights_column, weightVec);
      if (strataVec != null)
        f.add(_parms._strata_column, strataVec);
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
      o._var_coef = malloc2DArray(n_coef, n_coef);
      o._x_mean_cat = new double[o.data_info.numCats()][];
      o._x_mean_num = new double[o.data_info.numNums()][];
      o._mean_offset = MemoryManager.malloc8d(n_offsets);
      o._offset_names = new String[n_offsets];
      System.arraycopy(coefNames, n_coef, o._offset_names, 0, n_offsets);

      final double[] time = CollectTimes.collect(p.stopVec());
      final int n_time = time.length * (p.isStratified() ? 1 + (int) dinfo._adaptedFrame.vec(p._strata_column).max() : 1);
      o._time = time;
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
      o._x_mean_cat = new double[coxMR.sumWeights.length][];
      o._x_mean_num = new double[coxMR.sumWeights.length][];
      for (int s = 0; s < coxMR.sumWeights.length; s++) {
        o._x_mean_cat[s] = coxMR.sumWeightedCatX[s];
        for (int j = 0; j < o._x_mean_cat[s].length; j++)
          o._x_mean_cat[s][j] /= coxMR.sumWeights[s];
        o._x_mean_num[s] = coxMR.sumWeightedNumX[s];
        for (int j = 0; j < o._x_mean_num[s].length; j++)
          o._x_mean_num[s][j] = o.data_info._normSub[j] + o._x_mean_num[s][j] / coxMR.sumWeights[s];
      }
      System.arraycopy(o.data_info._normSub, o.data_info.numNums(), o._mean_offset, 0, o._mean_offset.length);
      for (int t = 0; t < coxMR.countEvents.length; ++t) {
        o._total_event += coxMR.countEvents[t];
        if (coxMR.sizeEvents[t] > 0 || coxMR.sizeCensored[t] > 0) {
          o._n_risk[t]   = coxMR.sizeRiskSet[t];
          o._n_event[t]  = coxMR.sizeEvents[t];
          o._n_censor[t] = coxMR.sizeCensored[t];
        }
      }
      if (p._start_column == null)
        for (int t = o._n_risk.length - 2; t >= 0; --t)
          o._n_risk[t] += o._n_risk[t + 1];
    }

    private class EfronUpdateFun extends MrFun<EfronUpdateFun> {
      transient CoxPHTask _coxMR;
      int _n_coef;
      double _logLik;
      double[] _gradient;
      double[][] _hessian;

      private EfronUpdateFun(ComputationState cs, CoxPHTask coxMR) {
        _coxMR = coxMR;
        _n_coef = cs._n_coef;
        _logLik = cs._logLik;
        _gradient = cs._gradient;
        _hessian = cs._hessian;
      }

      @Override
      protected void map(int t) {
        final double sizeEvents_t = _coxMR.sizeEvents[t];
        if (sizeEvents_t > 0) {
          final long   countEvents_t      = _coxMR.countEvents[t];
          final double sumLogRiskEvents_t = _coxMR.sumLogRiskEvents[t];
          final double sumRiskEvents_t    = _coxMR.sumRiskEvents[t];
          final double rcumsumRisk_t      = _coxMR.rcumsumRisk[t];
          final double avgSize            = sizeEvents_t / countEvents_t;
          _logLik += sumLogRiskEvents_t;
          for (int i = 0; i < _n_coef; i++)
            _gradient[i] += _coxMR.sumXEvents[t][i];
          for (long e = 0; e < countEvents_t; ++e) {
            final double frac = ((double) e) / ((double) countEvents_t);
            final double term = rcumsumRisk_t - frac * sumRiskEvents_t;
            _logLik -= avgSize * Math.log(term);
            for (int j = 0; j < _n_coef; ++j) {
              final double djTerm    = _coxMR.rcumsumXRisk[t][j] - frac * _coxMR.sumXRiskEvents[t][j];
              final double djLogTerm = djTerm / term;
              _gradient[j] -= avgSize * djLogTerm;
              for (int k = 0; k < _n_coef; ++k) {
                final double dkTerm  = _coxMR.rcumsumXRisk[t][k] - frac * _coxMR.sumXRiskEvents[t][k];
                final double djkTerm = _coxMR.rcumsumXXRisk[t][j][k] - frac * _coxMR.sumXXRiskEvents[t][j][k];
                _hessian[j][k] -= avgSize * (djkTerm / term - (djLogTerm * (dkTerm / term)));
              }
            }
          }
        }
      }

      @Override
      protected void reduce(EfronUpdateFun o) {
        _logLik += o._logLik;
        for (int i = 0; i < _n_coef; i++)
          _gradient[i] += o._gradient[i];
        for (int i = 0; i < _n_coef; i++)
          for (int j = 0; j < _n_coef; j++)
            _hessian[i][j] += o._hessian[i][j];
      }

      @Override
      protected MrFun<EfronUpdateFun> makeCopy() {
        return new EfronUpdateFun(new ComputationState(_n_coef), _coxMR);
      }

      ComputationState toComputationState(ComputationState cs) {
        cs._logLik = _logLik;
        cs._gradient = _gradient;
        cs._hessian = _hessian;
        return cs;
      }
    }

    protected ComputationState calcLoglik(ComputationState cs, CoxPHModel.CoxPHParameters p, final CoxPHTask coxMR) {
      final int n_coef = cs._n_coef;
      final int n_time = coxMR.sizeEvents.length;

      cs.reset();
      switch (p._ties) {
        case efron:
          EfronUpdateFun f = new EfronUpdateFun(cs, coxMR);
          H2O.submitTask(new LocalMR(f, n_time)).join();
          return f.toComputationState(cs);
        case breslow:
          double newLoglik = 0;
          for (int t = n_time - 1; t >= 0; --t) {
            final double sizeEvents_t = coxMR.sizeEvents[t];
            if (sizeEvents_t > 0) {
              final double sumLogRiskEvents_t = coxMR.sumLogRiskEvents[t];
              final double rcumsumRisk_t      = coxMR.rcumsumRisk[t];
              newLoglik += sumLogRiskEvents_t;
              newLoglik -= sizeEvents_t * Math.log(rcumsumRisk_t);
              for (int j = 0; j < n_coef; ++j) {
                final double dlogTerm = coxMR.rcumsumXRisk[t][j] / rcumsumRisk_t;
                cs._gradient[j] += coxMR.sumXEvents[t][j];
                cs._gradient[j] -= sizeEvents_t * dlogTerm;
                for (int k = 0; k < n_coef; ++k)
                  cs._hessian[j][k] -= sizeEvents_t *
                          (((coxMR.rcumsumXXRisk[t][j][k] / rcumsumRisk_t) -
                                  (dlogTerm * (coxMR.rcumsumXRisk[t][k] / rcumsumRisk_t))));
              }
            }
          }
          cs._logLik =  newLoglik;
          return cs;
        default:
          throw new IllegalArgumentException("_ties method must be either efron or breslow");
      }
    }

    protected void calcModelStats(CoxPHModel model, final double[] newCoef, final ComputationState cs) {
      CoxPHModel.CoxPHParameters p = model._parms;
      CoxPHModel.CoxPHOutput o = model._output;

      final int n_coef = o._coef.length;
      final Matrix inv_hessian = new Matrix(cs._hessian).inverse();
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
        o._null_loglik = cs._logLik;
        o._maxrsq = 1 - Math.exp(2 * o._null_loglik / o._n);
        o._score_test = 0;
        for (int j = 0; j < n_coef; ++j) {
          double sum = 0;
          for (int k = 0; k < n_coef; ++k)
            sum += o._var_coef[j][k] * cs._gradient[k];
          o._score_test += cs._gradient[j] * sum;
        }
      }
      o._loglik = cs._logLik;
      o._loglik_test = - 2 * (o._null_loglik - o._loglik);
      o._rsq = 1 - Math.exp(- o._loglik_test / o._n);
      o._wald_test = 0;
      for (int j = 0; j < n_coef; ++j) {
        double sum = 0;
        for (int k = 0; k < n_coef; ++k)
          sum -= cs._hessian[j][k] * (o._coef[k] - p._init);
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

        IcedHashMap<AstGroup.G, IcedInt> strataMap = new IcedHashMap<>();
        Frame f = reorderTrainFrameColumns(strataMap);

        int nResponses = (_parms.startVec() == null ? 2 : 3) + (_parms.isStratified() ? 1 : 0);
        final DataInfo dinfo = new DataInfo(f, null, nResponses, _parms._use_all_factor_levels, TransformType.DEMEAN, TransformType.NONE, true, false, false, false, false, false, _parms.interactionSpec())
                .disableIntercept();
        Scope.track_generic(dinfo);
        DKV.put(dinfo);

        // The model to be built
        CoxPHModel.CoxPHOutput output = new CoxPHModel.CoxPHOutput(CoxPH.this, dinfo._adaptedFrame, train(), strataMap);
        model = new CoxPHModel(_job._result, _parms, output);
        model.delete_and_lock(_job);

        initStats(model, dinfo);
        ScoringHistory sc = new ScoringHistory(_parms._iter_max + 1);

        final int n_offsets = (_offset == null) ? 0 : 1;
        final int n_coef = dinfo.fullN() - n_offsets;
        final double[] step = MemoryManager.malloc8d(n_coef);
        final double[] oldCoef = MemoryManager.malloc8d(n_coef);
        final double[] newCoef = MemoryManager.malloc8d(n_coef);
        Arrays.fill(step, Double.NaN);
        Arrays.fill(oldCoef, Double.NaN);
        for (int j = 0; j < n_coef; ++j)
          newCoef[j] = model._parms._init;
        double logLik = -Double.MAX_VALUE;
        final boolean has_start_column = (model._parms.startVec() != null);
        final boolean has_weights_column = (_weights != null);
        final ComputationState cs = new ComputationState(n_coef);
        Timer iterTimer = null;
        for (int i = 0; i <= model._parms._iter_max; ++i) {
          iterTimer = new Timer();
          model._output._iter = i;

          Timer aggregTimer = new Timer();
          final CoxPHTask coxMR = new CoxPHTask(_job._key, dinfo, newCoef, model._output._time, (long) response().min() /* min event */,
                  n_offsets, has_start_column, dinfo._adaptedFrame.vec(_parms._strata_column), has_weights_column).doAll(dinfo._adaptedFrame);
          Log.info("CoxPHTask: iter=" + i + ", " + aggregTimer.toString());

          Timer loglikTimer = new Timer();
          final double newLoglik = calcLoglik(cs, _parms, coxMR)._logLik;
          Log.info("LogLik: iter=" + i + ", time=" + loglikTimer.toString() + ", logLig=" + newLoglik);
          model._output._scoring_history = sc.addIterationScore(i, newLoglik).to2dTable(i);

          if (newLoglik > logLik) {
            if (i == 0)
              calcCounts(model, coxMR);

            calcModelStats(model, newCoef, cs);
            calcCumhaz_0(model, coxMR);

            if (newLoglik == 0)
              model._output._lre = -Math.log10(Math.abs(logLik - newLoglik));
            else
              model._output._lre = -Math.log10(Math.abs((logLik - newLoglik) / newLoglik));
            if (model._output._lre >= model._parms._lre_min)
              break;

            Arrays.fill(step, 0);
            for (int j = 0; j < n_coef; ++j)
              for (int k = 0; k < n_coef; ++k)
                step[j] -= model._output._var_coef[j][k] * cs._gradient[k];
            for (int j = 0; j < n_coef; ++j)
              if (Double.isNaN(step[j]) || Double.isInfinite(step[j]))
                break;

            logLik = newLoglik;
            System.arraycopy(newCoef, 0, oldCoef, 0, oldCoef.length);
          } else {
            for (int j = 0; j < n_coef; ++j)
              step[j] /= 2;
          }

          for (int j = 0; j < n_coef; ++j)
            newCoef[j] = oldCoef[j] - step[j];

          model.update(_job);
          _job.update(1, "Iteration = " + i + "/" + model._parms._iter_max + ", logLik = " + logLik);
          if (i != model._parms._iter_max)
            Log.info("CoxPH Iteration: iter=" + i + ", " + iterTimer.toString());
        }

        if (iterTimer != null)
          Log.info("CoxPH Last Iteration: " + iterTimer.toString());

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
    private final double[] _time;
    private final int      _n_offsets;
    private final boolean  _has_start_column;
    private final boolean  _has_strata_column;
    private final boolean  _has_weights_column;
    private final long     _min_event;
    private final int      _num_strata; // = 1 if the model is not stratified

    long         n;
    double[]     sumWeights;
    double[][]   sumWeightedCatX;
    double[][]   sumWeightedNumX;
    double[]     sizeRiskSet;
    double[]     sizeCensored;
    double[]     sizeEvents;
    long[]       countEvents;
    double[][]   sumXEvents;
    double[]     sumRiskEvents;
    double[][]   sumXRiskEvents;
    double[][][] sumXXRiskEvents;
    double[]     sumLogRiskEvents;
    double[]     rcumsumRisk;
    double[][]   rcumsumXRisk;
    double[][][] rcumsumXXRisk;

    CoxPHTask(Key<Job> jobKey, DataInfo dinfo, final double[] beta, final double[] time, final long min_event,
              final int n_offsets, final boolean has_start_column, Vec strata_column, final boolean has_weights_column) {
      super(jobKey, dinfo);
      _beta               = beta;
      _time = time;
      _min_event          = min_event;
      _n_offsets          = n_offsets;
      _has_start_column   = has_start_column;
      _has_strata_column  = strata_column != null;
      _has_weights_column = has_weights_column;
      _num_strata         = _has_strata_column ? 1 + (int) strata_column.max() : 1;
    }

    @Override
    protected boolean chunkInit(){
      final int n_time = _time.length * _num_strata;
      final int n_coef = _beta.length;

      sumWeights       = MemoryManager.malloc8d(_num_strata);
      sumWeightedCatX  = malloc2DArray(_num_strata, _dinfo.numCats());
      sumWeightedNumX  = malloc2DArray(_num_strata, _dinfo.numNums());
      sizeRiskSet      = MemoryManager.malloc8d(n_time);
      sizeCensored     = MemoryManager.malloc8d(n_time);
      sizeEvents       = MemoryManager.malloc8d(n_time);
      countEvents      = MemoryManager.malloc8(n_time);
      sumRiskEvents    = MemoryManager.malloc8d(n_time);
      sumLogRiskEvents = MemoryManager.malloc8d(n_time);
      rcumsumRisk      = MemoryManager.malloc8d(n_time);
      sumXEvents       = malloc2DArray(n_time, n_coef);
      sumXRiskEvents   = malloc2DArray(n_time, n_coef);
      rcumsumXRisk     = malloc2DArray(n_time, n_coef);
      sumXXRiskEvents  = malloc3DArray(n_time, n_coef, n_coef);
      rcumsumXXRisk    = malloc3DArray(n_time, n_coef, n_coef);
      return true;
    }

    @Override
    protected void processRow(long gid, Row row) {
      n++;
      double [] response = row.response;
      int ncats = row.nBins;
      int [] cats = row.binIds;
      double [] nums = row.numVals;
      final double weight = _has_weights_column ? response[0] : 1.0;
      if (weight <= 0)
        throw new IllegalArgumentException("weights must be positive values");
      int respIdx = response.length - 1;
      final long event = (long) (response[respIdx--] - _min_event);
      double stopTime = response[respIdx--];
      double startTime = _has_start_column ? response[respIdx--] : _time[0] - 1;
      double strata = _has_strata_column ? response[respIdx--] : 0;
      assert respIdx == -1 : "expected to use all response data";
      if (Double.isNaN(strata))
        return; // skip this row
      final int strataId = (int) strata;
      int t1c = Arrays.binarySearch(_time, startTime);
      int t1 = (t1c < 0) ? -t1c - 1 : t1c + 1;
      int t2 = Arrays.binarySearch(_time, stopTime);
      if (t2 < 0)
        throw new IllegalStateException("Encountered unexpected stop time");
      if (t1 > t2)
        throw new IllegalArgumentException("start times must be strictly less than stop times");
      final int strataOffset = _time.length * strataId;
      t1 += strataOffset;
      t2 += strataOffset;
      final int numStart = _dinfo.numStart();
      sumWeights[strataId] += weight;
      for (int j = 0; j < ncats; ++j)
        sumWeightedCatX[strataId][cats[j]] += weight;
      for (int j = 0; j < nums.length; ++j)
        sumWeightedNumX[strataId][j] += weight * nums[j];
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
      ArrayUtils.add(sumWeights,       that.sumWeights);
      ArrayUtils.add(sumWeightedCatX,  that.sumWeightedCatX);
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
          rcumsumRisk[t] += ((t + 1) % _time.length) == 0 ? 0 : rcumsumRisk[t + 1];

        for (int t = rcumsumXRisk.length - 2; t >= 0; --t)
          for (int j = 0; j < rcumsumXRisk[t].length; ++j)
            rcumsumXRisk[t][j] += ((t + 1) % _time.length) == 0 ? 0 : rcumsumXRisk[t + 1][j];

        for (int t = rcumsumXXRisk.length - 2; t >= 0; --t)
          for (int j = 0; j < rcumsumXXRisk[t].length; ++j)
            for (int k = 0; k < rcumsumXXRisk[t][j].length; ++k)
              rcumsumXXRisk[t][j][k] += ((t + 1) % _time.length) == 0 ? 0 : rcumsumXXRisk[t + 1][j][k];
      }
    }
  }

  private static class CollectTimes extends VecUtils.CollectDoubleDomain {
    private CollectTimes() {
      super(new double[0], MAX_TIME_BINS);
    }
    static double[] collect(Vec timeVec) {
      return new CollectTimes().doAll(timeVec).domain();
    }
    @Override
    protected void onMaxDomainExceeded(int maxDomainSize, int currentSize) {
      throw new CollectTimesException("number of distinct stop times is at least " + currentSize + "; maximum number allowed is " + maxDomainSize);
    }
  }

  private static class CollectTimesException extends RuntimeException {
    private CollectTimesException(String message) {
      super(message);
    }
  }

  private static class ComputationState {
    final int _n_coef;
    double _logLik;
    double[] _gradient;
    double[][] _hessian;

    ComputationState(int n_coef) {
      _n_coef = n_coef;
      _logLik = 0;
      _gradient = MemoryManager.malloc8d(n_coef);
      _hessian = malloc2DArray(n_coef, n_coef);
    }

    void reset() {
      _logLik = 0;
      for (int j = 0; j < _n_coef; ++j)
        _gradient[j] = 0;
      for (int j = 0; j < _n_coef; ++j)
        for (int k = 0; k < _n_coef; ++k)
          _hessian[j][k] = 0;
    }

  }

  private static class ScoringHistory {
    private long[]_scoringTimes;
    private double[] _logLiks;

    public ScoringHistory(int iterCnt) {
      _scoringTimes = new long[iterCnt];
      _logLiks = new double[iterCnt];
    }

    public ScoringHistory addIterationScore(int iter, double logLik) {
      _scoringTimes[iter] = System.currentTimeMillis();
      _logLiks[iter] = logLik;
      return this;
    }

    public TwoDimTable to2dTable(int iterCnt) {
      String[] cnames = new String[]{"timestamp", "duration", "iterations", "logLik"};
      String[] ctypes = new String[]{"string", "string", "int", "double"};
      String[] cformats = new String[]{"%s", "%s", "%d", "%.5f"};
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[iterCnt], cnames, ctypes, cformats, "");
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      for (int i = 0; i < iterCnt; i++) {
        int col = 0;
        res.set(i, col++, fmt.print(_scoringTimes[i]));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes[i] - _scoringTimes[0], true));
        res.set(i, col++, i);
        res.set(i, col++, _logLiks[i]);
      }
      return res;
    }
  }

}
