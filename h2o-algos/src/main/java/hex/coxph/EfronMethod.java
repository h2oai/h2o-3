package hex.coxph;

import hex.DataInfo;
import water.*;
import water.util.ArrayUtils;

import static hex.coxph.CoxPH.CoxPHTask;
import static hex.coxph.CoxPH.ComputationState;

class EfronMethod {

  static ComputationState calcLoglik(DataInfo dinfo, CoxPHTask coxMR, ComputationState cs, boolean runLocal) {
    EfronDJKSetupFun djkTermSetup = EfronDJKSetupFun.setupEfron(coxMR);
    EfronDJKTermTask djkTermTask = new EfronDJKTermTask(dinfo, coxMR, djkTermSetup)
            .doAll(dinfo._adaptedFrame, runLocal);
    EfronUpdateFun f = new EfronUpdateFun(cs, coxMR);
    LocalMR<EfronUpdateFun> efronMR = makeEfronMRTask(f, coxMR.sizeEvents.length);
    H2O.submitTask(efronMR).join();
    for (int i = 0; i < f._n_coef; i++)
      for (int j = 0; j < f._n_coef; j++)
        f._hessian[i][j] += djkTermTask._djkTerm[i][j];
    for (int i = 0; i < f._n_coef; i++)
      f._gradient[i] += coxMR.sumXEvents[i];
    return f.toComputationState(cs);
  }

  // We are dealing with doubles - order of summations in floating point math matter!
  // In order to have a deterministic order we need to disable "previous task reuse" - that will give us
  // a deterministic order of applying reduce operations.
  static LocalMR<EfronUpdateFun> makeEfronMRTask(EfronUpdateFun f, int nEvents) {
    return new LocalMR<EfronUpdateFun>(f, nEvents)
            .withNoPrevTaskReuse();
  }

}

class EfronDJKSetupFun extends MrFun<EfronDJKSetupFun> {

  private final CoxPHTask _coxMR;

  double[] _riskTermT2;
  double[] _cumsumRiskTerm;

  public EfronDJKSetupFun() { _coxMR = null; }

  private EfronDJKSetupFun(CoxPHTask coxMR) {
    _coxMR = coxMR;
    _riskTermT2 = new double[coxMR.sizeEvents.length];
    _cumsumRiskTerm = new double[coxMR.sizeEvents.length];
  }

  @Override
  protected void map(int t) {
    final double sizeEvents_t = _coxMR.sizeEvents[t];
    final long countEvents_t = _coxMR.countEvents[t];
    final double sumRiskEvents_t = _coxMR.sumRiskEvents[t];
    final double rcumsumRisk_t = _coxMR.rcumsumRisk[t];
    final double avgSize = sizeEvents_t / countEvents_t;

    for (long e = 0; e < countEvents_t; ++e) {
      final double frac = ((double) e) / ((double) countEvents_t);
      final double term = rcumsumRisk_t - frac * sumRiskEvents_t;
      _riskTermT2[t] += avgSize * frac / term;
      _cumsumRiskTerm[t] += avgSize / term;
    }
  }

  private EfronDJKSetupFun postProcess() {
    final int timeLen = _coxMR._time.length;
    for (int t = 1; t < _cumsumRiskTerm.length; t++) {
      _cumsumRiskTerm[t] += (t % timeLen) == 0 ? 0 : _cumsumRiskTerm[t - 1];
    }

    return this;
  }

  static EfronDJKSetupFun setupEfron(CoxPHTask coxMR) {
    EfronDJKSetupFun djkTermSetup = new EfronDJKSetupFun(coxMR);
    H2O.submitTask(new LocalMR(djkTermSetup, coxMR.sizeEvents.length)).join();
    return djkTermSetup.postProcess();
  }

}

class EfronDJKTermTask extends CPHBaseTask<EfronDJKTermTask> {

  private double[]       _cumsumRiskTerm;
  private double[]       _riskTermT2;
  private double[]       _beta;
  private final int      _n_offsets;
  private final int      _n_time;
  private final long     _min_event;
  private final boolean  _has_weights_column;
  private final boolean  _has_start_column;
  private final boolean  _has_strata_column;

  // OUT
  double[][] _djkTerm;

  EfronDJKTermTask(DataInfo dinfo, CoxPHTask coxMR, EfronDJKSetupFun setup) {
    super(dinfo);
    _cumsumRiskTerm = setup._cumsumRiskTerm;
    _riskTermT2 = setup._riskTermT2;
    _beta = coxMR._beta;
    _n_offsets = coxMR._n_offsets;
    _n_time = coxMR._time.length;
    _min_event = coxMR._min_event;
    _has_weights_column = coxMR._has_weights_column;
    _has_start_column = coxMR._has_start_column;
    _has_strata_column = coxMR._has_strata_column;
  }

  @Override
  protected void chunkInit() {
    final int n_coef = _beta.length;
    _djkTerm = MemoryManager.malloc8d(n_coef, n_coef);
  }

  @Override
  protected void processRow(DataInfo.Row row) {
    double [] response = row.response;
    int ncats = row.nBins;
    int [] cats = row.binIds;
    double [] nums = row.numVals;
    final double weight = _has_weights_column ? row.weight : 1.0;
    if (weight <= 0)
      throw new IllegalArgumentException("weights must be positive values");
    int respIdx = response.length - 1;
    final long event = (long) (response[respIdx--] - _min_event);
    final int t2 = (int) response[respIdx--];
    int t1 = _has_start_column ? (int) response[respIdx--] : -1;
    final double strata = _has_strata_column ? response[respIdx--] : 0;
    assert respIdx == -1 : "expected to use all response data";
    if (Double.isNaN(strata))
      return; // skip this row

    final int numStart = _dinfo.numStart();

    // risk is cheaper to recalculate than trying to re-use risk calculated in CoxPHTask
    double logRisk = 0;
    for (int j = 0; j < ncats; ++j)
      logRisk += _beta[cats[j]];
    for (int j = 0; j < nums.length - _n_offsets; ++j)
      logRisk += nums[j] * _beta[numStart + j];
    for (int j = nums.length - _n_offsets; j < nums.length; ++j)
      logRisk += nums[j];
    final double risk = weight * Math.exp(logRisk);

    final int ntotal = ncats + (nums.length - _n_offsets);
    final int numStartIter = numStart - ncats;

    final double cumsumRiskTerm;
    if (_has_start_column && (t1 % _n_time > 0)) {
      cumsumRiskTerm = _cumsumRiskTerm[t2] - _cumsumRiskTerm[t1 - 1];
    } else {
      cumsumRiskTerm = _cumsumRiskTerm[t2];
    }
    final double riskTermT2 = event > 0 ? _riskTermT2[t2] : 0;
    final double mult = (riskTermT2 - cumsumRiskTerm) * risk;

    for (int jit = 0; jit < ntotal; ++jit) {
      final boolean jIsCat = jit < ncats;
      final int j          = jIsCat ? cats[jit] : numStartIter + jit;
      final double x1      = jIsCat ? 1.0 : nums[jit - ncats];
      final double x1mult  = x1 * mult;
      for (int kit = jit; kit < ntotal; ++kit) {
        final boolean kIsCat = kit < ncats;
        final int k          = kIsCat ? cats[kit] : numStartIter + kit;
        final double x2      = kIsCat ? 1.0 : nums[kit - ncats];
        _djkTerm[j][k] += x1mult * x2;
      }
    }
  }

  @Override
  protected void closeLocal() {
    // to avoid sending them back over the wire
    _cumsumRiskTerm = null;
    _riskTermT2 = null;
    _beta = null;
  }

  @Override
  public void reduce(EfronDJKTermTask that) {
    ArrayUtils.add(_djkTerm, that._djkTerm);
  }

  @Override
  protected void postGlobal() {
    for (int j = 1; j < _djkTerm.length; j++) {
      for (int k = 0; k < j; k++)
        _djkTerm[j][k] = _djkTerm[k][j];
    }
  }

}

class EfronUpdateFun extends MrFun<EfronUpdateFun> {
  transient CoxPHTask _coxMR;

  int _n_coef;
  double _logLik;
  double[] _gradient;
  double[][] _hessian;

  EfronUpdateFun(ComputationState cs, CoxPHTask coxMR) {
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
            _hessian[j][k] += avgSize * (djLogTerm * (dkTerm / term));
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
