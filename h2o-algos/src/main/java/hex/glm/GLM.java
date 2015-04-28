package hex.glm;

import hex.DataInfo;
import hex.Model;
import hex.SupervisedModelBuilder;
import hex.glm.GLMModel.FinalizeAndUnlockTsk;
import hex.glm.GLMModel.GLMOutput;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMTask.*;
import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import hex.gram.Gram.NonSPDMatrixException;
import hex.optimization.ADMM;
import hex.optimization.ADMM.ProximalSolver;
import hex.optimization.L_BFGS;
import hex.optimization.L_BFGS.*;
import hex.schemas.GLMV3;
import hex.schemas.ModelBuilderSchema;
import jsr166y.CountedCompleter;
import water.*;
import water.H2O.H2OCallback;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.H2O.H2OCountedCompleter;
import water.parser.ValueString;
import water.util.*;
import water.util.MRUtils.ParallelTasks;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 */
public class GLM extends SupervisedModelBuilder<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  static final double LINE_SEARCH_STEP = .5;
  static final int NUM_LINE_SEARCH_STEPS = 16;
  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
            Model.ModelCategory.Regression,
            Model.ModelCategory.Binomial,
    };
  }

  public GLM(Key dest, String desc, GLMModel.GLMParameters parms) { super(dest, desc, parms); init(false); }
  public GLM(GLMModel.GLMParameters parms) { super("GLM", parms); init(false); }

  static class TooManyPredictorsException extends RuntimeException {}

  private BetaConstraint _bc = new BetaConstraint();
  DataInfo _dinfo;
  private Vec _rowFilter;
  private transient GLMTaskInfo [] _tInfos;
  private int _lambdaId;
  private transient DataInfo _validDinfo;
  private transient ArrayList<Integer> _scoring_iters = new ArrayList<>();
  private transient ArrayList<Double> _likelihoods = new ArrayList<>();
  private transient ArrayList<Double> _objectives = new ArrayList<>();
  private transient double _iceptAdjust = 0;

  @Override public void init(boolean expensive) {
    super.init(expensive);
    hide("_score_each_iteration", "Not used by GLM.");
    hide("_balance_classes", "Not applicable since class balancing is not required for GLM.");
    hide("_max_after_balance_size", "Not applicable since class balancing is not required for GLM.");
    hide("_class_sampling_factors", "Not applicable since class balancing is not required for GLM.");
    _parms.validate(this);
    if (expensive) {
      if(_parms._lambda_search || !_parms._intercept)
        _parms._use_all_factor_levels= true;
      if(_parms._max_active_predictors == -1)
        _parms._max_active_predictors = _parms._solver == Solver.IRLSM ?6000:100000000;
      if (_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
      _dinfo = new DataInfo(Key.make(), _train, _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
      DKV.put(_dinfo._key, _dinfo);
      // handle BetaConstraints if I got them
      double[] betaStart = null;
      double[] betaGiven = null;
      double[] betaLB = null;
      double[] betaUB = null;
      double[] rho = null;
      if (_parms._beta_constraints != null) {
        Frame beta_constraints = _parms._beta_constraints.get();
        Vec v = beta_constraints.vec("names");
        String[] dom;
        int[] map;
        if (v.isString()) {
          dom = new String[(int) v.length()];
          map = new int[dom.length];
          ValueString vs = new ValueString();
          for (int i = 0; i < dom.length; ++i) {
            dom[i] = v.atStr(vs, i).toString();
            map[i] = i;
          }
        } else if (v.isEnum()) {
          dom = v.domain();
          map = FrameUtils.asInts(v);
        } else
          throw new IllegalArgumentException("Illegal beta constraints file, names column expected to contain column names (strings)");
        // for now only enums allowed here
        String[] names = ArrayUtils.append(_dinfo.coefNames(), "Intercept");
        if (!Arrays.deepEquals(dom, names)) { // need mapping
          HashMap<String, Integer> m = new HashMap<String, Integer>();
          for (int i = 0; i < names.length; ++i)
            m.put(names[i], i);
          int[] newMap = MemoryManager.malloc4(dom.length);
          for (int i = 0; i < map.length; ++i) {
            Integer I = m.get(dom[map[i]]);
            if (I == null)
              throw new IllegalArgumentException("Unrecognized coefficient name in beta-constraint file, unknown name '" + dom[map[i]] + "'");
            newMap[i] = I == null ? -1 : I;
          }
          map = newMap;
        }
        final int numoff = _dinfo.numStart();
        if ((v = beta_constraints.vec("beta_start")) != null) {
          betaStart = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          for (int i = 0; i < (int) v.length(); ++i)
            betaStart[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("beta_given")) != null) {
          betaGiven = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          for (int i = 0; i < (int) v.length(); ++i)
            betaGiven[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("upper_bounds")) != null) {
          betaUB = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          Arrays.fill(betaUB, Double.POSITIVE_INFINITY);
          for (int i = 0; i < (int) v.length(); ++i)
            betaUB[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("lower_bounds")) != null) {
          betaLB = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          Arrays.fill(betaLB, Double.NEGATIVE_INFINITY);
          for (int i = 0; i < (int) v.length(); ++i)
            betaLB[map == null ? i : map[i]] = v.at(i);
        }
        if ((v = beta_constraints.vec("rho")) != null) {
          rho = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
          for (int i = 0; i < (int) v.length(); ++i)
            rho[map == null ? i : map[i]] = v.at(i);
        }
        if (_dinfo._normMul != null) {
          double normG = 0, normS = 0;
          for (int i = numoff; i < _dinfo.fullN(); ++i) {
            double s = _dinfo._normSub[i - numoff];
            double d = 1.0 / _dinfo._normMul[i - numoff];
            if (betaUB != null && !Double.isInfinite(betaUB[i]))
              betaUB[i] *= d;
            if (betaLB != null && !Double.isInfinite(betaUB[i]))
              betaLB[i] *= d;
            if (betaGiven != null) {
              normG += betaGiven[i] * s;
              betaGiven[i] *= d;
            }
            if (betaStart != null) {
              normS += betaStart[i] * s;
              betaStart[i] *= d;
            }
          }
          if (_dinfo._intercept) {
            int n = _dinfo.fullN();
            if (betaGiven != null)
              betaGiven[n] -= normG;
            if (betaStart != null)
              betaStart[n] -= normS;
          }
        }
        if(betaStart == null && betaGiven != null)
          betaStart = betaGiven.clone();
        if(betaStart != null) {
          if (betaLB != null || betaUB != null) {
            for (int i = 0; i < betaStart.length; ++i) {
              if (betaLB != null && betaLB[i] > betaStart[i])
                betaStart[i] = betaLB[i];
              if (betaUB != null && betaUB[i] < betaStart[i])
                betaStart[i] = betaUB[i];
            }
          }
        }
        _bc.setBetaStart(betaStart).setLowerBounds(betaLB).setUpperBounds(betaUB).setProximalPenalty(betaGiven, rho);
      }
      _tInfos = new GLMTaskInfo[_parms._n_folds + 1];
      InitTsk itsk = new InitTsk(0, _parms._intercept, null);
      H2O.submitTask(itsk).join();
      assert itsk._ymut != null;
      assert itsk._ymut._nobs == 0 || itsk._gtNull != null;
      assert itsk._ymut._nobs == 0 || itsk._ymut._nobs == itsk._gtNull._nobs:"unexpected nobs, " + itsk._ymut._nobs + " != " + itsk._gtNull._nobs;// +", filterVec = " + (itsk._gtNull._rowFilter != null) + ", nrows = " + itsk._gtNull._rowFilter.length() + ", mean = " + itsk._gtNull._rowFilter.mean()
      _rowFilter = itsk._ymut._fVec;
      assert _rowFilter.nChunks() == _dinfo._adaptedFrame.anyVec().nChunks();
      assert (_dinfo._adaptedFrame.numRows() - _rowFilter.mean() * _rowFilter.length()) == itsk._ymut._nobs:"unexpected nobs, expected " + itsk._ymut._nobs + ", but got " + _rowFilter.mean() * _rowFilter.length();
      assert _rowFilter != null;
      if (itsk._ymut._nobs == 0) { // can happen if all rows have missing value and we're filtering missing out
        error("training_frame", "Got no data to run on after filtering out the rows with missing values.");
        return;
      }
      if (itsk._ymut._yMin == itsk._ymut._yMax) {
        error("response", "Can not run glm on dataset with constant response. Response == " + itsk._ymut._yMin + " for all rows in the dataset after filtering out rows with NAs, got " + itsk._ymut._nobs + " rows out of " + _dinfo._adaptedFrame.numRows() + " rows total.");
        return;
      } if (itsk._ymut._nobs < (_dinfo._adaptedFrame.numRows() >> 1)) { // running less than half of rows?
        warn("training_frame", "Dataset has less than 1/2 of the data after filtering out rows with NAs");
      }
      if(_parms._prior > 0)
        _iceptAdjust = -Math.log(itsk._ymut._ymu * (1-_parms._prior)/(_parms._prior * (1-itsk._ymut._ymu)));
      // GLMTaskInfo(Key dstKey, int foldId, long nobs, double ymu, double lmax, double[] beta, GradientInfo ginfo, double objVal){
      GLMGradientTask gtBetastart = itsk._gtBetaStart != null?itsk._gtBetaStart:itsk._gtNull;
      _bc.adjustGradient(itsk._gtNull._beta,itsk._gtNull._gradient);
      if(_parms._alpha == null)
        _parms._alpha = new double[]{_parms._solver == Solver.IRLSM ?.5:0};
      double lmax =  lmax(itsk._gtNull);
      double objval = gtBetastart._likelihood/gtBetastart._nobs;
      double l2pen = .5 * lmax * (1 - _parms._alpha[0]) * ArrayUtils.l2norm2(gtBetastart._beta, _dinfo._intercept);
      l2pen += _bc.proxPen(gtBetastart._beta);
      objval += l2pen;
      _tInfos[0] = new GLMTaskInfo(_dest, 0, itsk._ymut._nobs, itsk._ymut._ymu,lmax,_bc._betaStart, _dinfo.fullN() + (_dinfo._intercept?1:0), new GLMGradientInfo(gtBetastart._likelihood,objval, gtBetastart._gradient), objVal(gtBetastart._likelihood,gtBetastart._beta, lmax, gtBetastart._nobs,_dinfo._intercept));

      if (_parms._lambda != null) { // check the lambdas
        ArrayUtils.mult(_parms._lambda, -1);
        Arrays.sort(_parms._lambda);
        ArrayUtils.mult(_parms._lambda, -1);
        int i = 0;
        while (i < _parms._lambda.length && _parms._lambda[i] > _tInfos[0]._lambdaMax) ++i;
        if (i == _parms._lambda.length)
          error("lambda", "All passed lambda values are > lambda_max = " + _tInfos[0]._lambdaMax + ", nothing to compute.");
        if (i > 0) {
          _parms._lambda = Arrays.copyOfRange(_parms._lambda, i, _parms._lambda.length);
          warn("lambda", "removed " + i + " lambda values which were greater than lambda_max = " + _tInfos[0]._lambdaMax);
        }
      } else { // fill in the default lambda(s)
        if (_parms._lambda_search) {
          if (_parms._nlambdas == 1)
            error("nlambdas", "Number of lambdas must be > 1 when running with lambda_search!");
          if (_parms._lambda_min_ratio == -1)
            _parms._lambda_min_ratio = _tInfos[0]._nobs > 25 * _dinfo.fullN() ? 1e-4 : 1e-2;
          final double d = Math.pow(_parms._lambda_min_ratio, 1.0 / (_parms._nlambdas - 1));
          _parms._lambda = MemoryManager.malloc8d(_parms._nlambdas);
          _parms._lambda[0] = _tInfos[0]._lambdaMax;
          for (int i = 1; i < _parms._lambda.length; ++i)
            _parms._lambda[i] = _parms._lambda[i - 1] * d;
        } else
          _parms._lambda = new double[]{_tInfos[0]._lambdaMax * (_dinfo.fullN() < (_tInfos[0]._nobs >> 4) ? 1e-3 : 1e-1)};
      }
      GLMModel m = new GLMModel(_dest, _parms, new GLMOutput(GLM.this), _dinfo, _tInfos[0]._ymu, _tInfos[0]._lambdaMax, _tInfos[0]._nobs);
      m.delete_and_lock(GLM.this._key);
      m.adaptTestForTrain(_valid, true);
      // _dinfo = new DataInfo(Key.make(), _train, _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
      if(_valid != null)
        _validDinfo = new DataInfo(Key.make(), _valid, null, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
      setSubmodel(_dest,0,_bc._betaStart,gtBetastart._val,null,null);
      if(_parms._solver == Solver.COORDINATE_DESCENT) { // make needed vecs
        double eta = _parms.link(_tInfos[0]._ymu);
        _tInfos[0]._eVec = _dinfo._adaptedFrame.anyVec().makeCon(eta);
        _tInfos[0]._wVec = _dinfo._adaptedFrame.anyVec().makeCon(1);
        _tInfos[0]._zVec = _dinfo._adaptedFrame.lastVec().makeCopy(null);
        _tInfos[0]._iVec = _dinfo._adaptedFrame.anyVec().makeCon(1);
      }
      if(_parms._max_iterations == -1) {
        if(_parms._solver == Solver.IRLSM) {
          _tInfos[0]._iterationsPerLambda = 10;
          _parms._max_iterations = _parms._lambda_search ? _tInfos[0]._iterationsPerLambda * _parms._nlambdas : 50;
        } else {
          _parms._max_iterations = Math.max(20,_dinfo.fullN() >> 2);
          if(_parms._lambda_search) {
            _tInfos[0]._iterationsPerLambda = Math.max(20,_parms._max_iterations / 20);
            _parms._max_iterations *= _parms._nlambdas*_tInfos[0]._iterationsPerLambda;
          }
        }
      }
      _tInfos[0]._workPerIteration = (int)(WORK_TOTAL /_parms._max_iterations);
    }
  }


  private class InitTsk extends H2OCountedCompleter {
    final int _foldId;
    final boolean _intercept;
    public InitTsk(int foldId, boolean intercept, H2OCountedCompleter cmp) { super(cmp); _foldId = foldId; _intercept = intercept; }
    YMUTask _ymut;
    GLMGradientTask _gtNull;
    GLMGradientTask _gtBetaStart;
    @Override
    protected void compute2() {
      // get filtered dataset's mean and number of observations
      new YMUTask(_dinfo, _dinfo._adaptedFrame.anyVec().makeZero(), new H2OCallback<YMUTask>(this) {
        @Override
        public void callback(final YMUTask ymut) {
          _rowFilter = ymut._fVec;
          _ymut = ymut;
          double ymu = _parms._intercept?_ymut._ymu:0;
          if(ymut._nobs > 0) {
            InitTsk.this.addToPendingCount(1);
            final double[] beta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
            if (_intercept)
              beta[beta.length - 1] = _parms.link(ymut._ymu);
            if (_bc._betaStart == null)
              _bc.setBetaStart(beta);
            // compute the lambda_max
            _gtNull = new GLMGradientTask(_dinfo, _parms, 0, beta, 1.0 / ymut._nobs, _rowFilter, InitTsk.this).setValidate(ymu,true).asyncExec(_dinfo._adaptedFrame);
            if (beta != _bc._betaStart) {
              InitTsk.this.addToPendingCount(1);
              _gtBetaStart = new GLMGradientTask(_dinfo, _parms, 0, _bc._betaStart, 1.0 / ymut._nobs, _rowFilter, InitTsk.this).setValidate(ymu,true).asyncExec(_dinfo._adaptedFrame);
            }
          }
        }
      }).asyncExec(_dinfo._adaptedFrame);
    }
  }
  @Override
  public ModelBuilderSchema schema() {
    return new GLMV3();
  }


  private static final long WORK_TOTAL = 1000000;
  @Override
  public Job<GLMModel> trainModel() {
    _parms.read_lock_frames(this);
    start(new GLMDriver(null), WORK_TOTAL);
    return this;
  }

  static double GLM_GRAD_EPS = 1e-4; // done (converged) if subgrad < this value.
  static final int sparseCoefThreshold = 750;
  ;

  public static class BetaConstraint extends Iced {
    double [] _betaStart;
    double [] _betaGiven;
    double [] _rho;
    double [] _betaLB;
    double [] _betaUB;
    public BetaConstraint setLowerBounds(double [] lb) {_betaLB = lb; return this;}
    public BetaConstraint setUpperBounds(double [] ub) {_betaUB = ub; return this;}
    public BetaConstraint setBetaStart  (double [] bs) {_betaStart = bs; return this;}
    public BetaConstraint setProximalPenalty  (double [] bGiven, double [] rho) {
      _betaGiven = bGiven;
      _rho = rho;
      return this;
    }

    public String toString(){
      double [][] ary = new double[_betaGiven.length][3];

      for(int i = 0; i < _betaGiven.length; ++i) {
        ary[i][0] = _betaGiven[i];
        ary[i][1] = _betaLB[i];
        ary[i][2] = _betaUB[i];
      }
      return ArrayUtils.pprint(ary);
    }
    public boolean hasBounds(){
      if(_betaLB != null)
        for(double d:_betaLB)
          if(!Double.isInfinite(d)) return true;
      if(_betaUB != null)
        for(double d:_betaUB)
          if(!Double.isInfinite(d)) return true;
      return false;
    }

    public void adjustGradient(double [] beta, double [] grad) {
      if(_betaGiven != null && _rho != null) {
        for(int i = 0; i < _betaGiven.length; ++i) {
          double diff = beta[i] - _betaGiven[i];
          grad[i]  += _rho[i] * diff;
        }
      }
    }
    double proxPen(double [] beta) {
      double res = 0;
      if(_betaGiven != null && _rho != null) {
        for(int i = 0; i < _betaGiven.length; ++i) {
          double diff = beta[i] - _betaGiven[i];
          res += _rho[i] * diff * diff;
        }
        res *= .5;
      }
      return res;
    }
  }

  /**
   * Encapsulates state of the computation.
   */
  public static final class GLMTaskInfo extends Iced {
    final int       _foldId;
    final long      _nobs;       // number of observations in our dataset
    final double    _ymu;        // actual mean of the response
    final double    _lambdaMax;  // lambda max of the current dataset
    double []       _beta;       // full - solution at previous lambda (or null)
    int    []       _activeCols;
    GLMGradientInfo    _ginfo;      // gradient and penalty of glm + L2 pen.transient double [] _activeBeta;
    double          _objVal;     // full objective value including L1 pen
    int             _iter;
    int             _workPerIteration;
    int             _iterationsPerLambda;
    int             _worked;     // total number of worked units
    // these are not strictly state variables
    // I put them here to have all needed info in state object (so I only need to keep State[] info when doing xval)
    final Key             _dstKey;

    // vecs used by cooridnate descent
    Vec _eVec; // eta
    Vec _wVec; // weights
    Vec _zVec; // z
    Vec _iVec; // intercept - all 1s
    final int _fullN;

    public GLMTaskInfo(Key dstKey, int foldId, long nobs, double ymu, double lmax, double[] beta, int fullN, GLMGradientInfo ginfo, double objVal){
      _dstKey = dstKey;
      _foldId = foldId;
      _nobs = nobs;
      _ymu = ymu;
      _lambdaMax = lmax;
      _beta = beta;
      _ginfo = ginfo;
      _objVal = objVal;
      _fullN = fullN;
    }


    public double gradientCheck(double lambda, double alpha){
      // assuming full-gradient, beta only for active columns
      double [] beta = expandVec(_beta,_activeCols, _fullN);
      double [] subgrad = _ginfo._gradient.clone();
      double err = 0;
      ADMM.subgrad(alpha*lambda,beta,subgrad);
      for(double d: subgrad)
        if(err < -d) err = -d; else if(err < d) err = d;
      Log.info("gerr at lambda = " + lambda + " = " + err);
      return err;
    }
    public void adjustToNewLambda( double currentLambda, double newLambda, double alpha, boolean intercept) {
      assert newLambda < currentLambda:"newLambda = " + newLambda + ", last lambda = " + currentLambda;
      double ldiff = (newLambda - currentLambda);
      double l2pen = .5 * (1-alpha) * ArrayUtils.l2norm2(_beta, intercept);
      double l1pen = alpha * ArrayUtils.l1norm(_beta, intercept);
      for (int i = 0; i < _ginfo._gradient.length - (intercept?1:0); ++i)
        _ginfo._gradient[i] += ldiff * (1-alpha) * _beta[i];
      _ginfo = new GLMGradientInfo(_ginfo._likelihood, _ginfo._objVal + ldiff * l2pen, _ginfo._gradient);
      _objVal = _objVal + ldiff * (l1pen + l2pen); //todo add proximal penalty?
    }
  }

  private final double lmax(GLMGradientTask gLmax) {
    return Math.max(ArrayUtils.maxValue(gLmax._gradient),-ArrayUtils.minValue(gLmax._gradient))/Math.max(1e-3,_parms._alpha[0]);
  }

  /**
   * Contains implementation of the glm algo.
   * It's a DTask so it can be computed on other nodes (to distributed single node part of the computation).
   */
  public final class GLMDriver extends DTask<GLMDriver> {
    transient AtomicBoolean _gotException = new AtomicBoolean();

    public GLMDriver(H2OCountedCompleter cmp){ super(cmp);}

    private void doCleanup(){
      _parms.read_unlock_frames(GLM.this);
      DKV.remove(_dinfo._key);
      if(_rowFilter != null)
        _rowFilter.remove();
      if(_tInfos != null && _tInfos[0] != null) {
        if (_tInfos[0]._wVec != null)
          _tInfos[0]._wVec.remove();
        if (_tInfos[0]._zVec != null)
          _tInfos[0]._zVec.remove();
        if (_tInfos[0]._eVec != null)
          _tInfos[0]._eVec.remove();
        if (_tInfos[0]._iVec != null)
          _tInfos[0]._iVec.remove();
      }
    }

    @Override public void onCompletion(CountedCompleter cc){
      getCompleter().addToPendingCount(1);
      int    [] its = null;
      double [] lgs = null;
      double [] obs = null;
      if(!_scoring_iters.isEmpty()) {
        its = new int[_scoring_iters.size()];
        lgs = new double[its.length];
        obs = new double[its.length];
        for(int i = 0; i < its.length; ++i) {
          its[i] = _scoring_iters.get(i);
          lgs[i] = _likelihoods.get(i);
          obs[i] = _objectives.get(i);
        }
      }
      H2O.submitTask(new FinalizeAndUnlockTsk(new H2OCallback((H2OCountedCompleter) getCompleter()) {
        @Override
        public void callback(H2OCountedCompleter h2OCountedCompleter) {
          doCleanup();
          done();
        }

        @Override
        public boolean onExceptionalCompletion(Throwable ex, CountedCompleter cc) {
          doCleanup();
          failed(ex);
          new RemoveCall(null, _dest).invokeTask();
          return true;
        }
      }, _dest, _key, _parms._train, _parms._valid,its, lgs, obs));
    }

    @Override public boolean onExceptionalCompletion(final Throwable ex, CountedCompleter cc){
      if(!_gotException.getAndSet(true)){
        if(ex instanceof TooManyPredictorsException){
          // TODO add a warning
          tryComplete();
          return false;
        }
        doCleanup();
        new RemoveCall(null, _dest).invokeTask();
        failed(ex);
        return true;
      }
      return false;
    }

    @Override
    protected void compute2() {
      init(true);
      // GLMModel(Key selfKey, GLMParameters parms, GLMOutput output, DataInfo dinfo, double ymu, double lambda_max, long nobs, float [] thresholds) {
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
      if(_parms._n_folds != 0)
        throw H2O.unimpl();
      //todo: fill in initialization for n-folds
      new GLMSingleLambdaTsk(new LambdaSearchIteration(this),_tInfos[0]).fork();
    }

    private class LambdaSearchIteration extends H2O.H2OCallback {
      public LambdaSearchIteration(H2OCountedCompleter cmp){super(cmp); }
      @Override
      public void callback(H2OCountedCompleter h2OCountedCompleter) {
        assert _tInfos[0]._ginfo._gradient.length == _dinfo.fullN()+1;
        int workDiff = (_lambdaId+1)*_tInfos[0]._iterationsPerLambda*_tInfos[0]._workPerIteration - _tInfos[0]._worked;
        if(workDiff > 0) {
          update(workDiff,"lambda = " + _lambdaId + ", iteration = " + _tInfos[0]._iter);
          _tInfos[0]._worked += workDiff;
        }
        int rank = 0;
        for(int i = 0; i < _tInfos[0]._beta.length - (_dinfo._intercept?1:0); ++i)
          if(_tInfos[0]._beta[i] != 0) ++rank;
        Log.info("Solution at lambda = " + _parms._lambda[_lambdaId] + " has " + rank + " nonzeros, gradient err = " + _tInfos[0].gradientCheck(_parms._lambda[_lambdaId], _parms._alpha[0]));
        if(_parms._n_folds > 1){
          // copy the state over
          ParallelTasks<GLMSingleLambdaTsk> t = (ParallelTasks<GLMSingleLambdaTsk>)h2OCountedCompleter;
          for(int i = 0; i < t._tasks.length; ++i)
            _tInfos[i] = t._tasks[i]._taskInfo;
          // launch xval-task to compute validations of xval models
          // getCompleter().addToPendingCount(1);
          // TODO ...
        }
        // launch the next lambda
        if(++_lambdaId  < _parms._lambda.length && _tInfos[0]._iter < _parms._max_iterations) {
          getCompleter().addToPendingCount(1);
          if(_parms._n_folds > 1) {
            GLMSingleLambdaTsk[] tasks = new GLMSingleLambdaTsk[_tInfos.length];
            H2OCountedCompleter cmp = new LambdaSearchIteration((H2OCountedCompleter)getCompleter());
            cmp.addToPendingCount(tasks.length-1);
            for(int i = 0; i < tasks.length; ++i)
              tasks[i] = new GLMSingleLambdaTsk(cmp,_tInfos[i]);
            new ParallelTasks(new LambdaSearchIteration((H2OCountedCompleter) getCompleter()),tasks).fork();
          } else {
            do {
              double currentLambda = _parms._lambda[_lambdaId-1];
              double nextLambda = _parms._lambda[_lambdaId];
              _tInfos[0].adjustToNewLambda(currentLambda, nextLambda, _parms._alpha[0], _dinfo._intercept);
            } while((_tInfos[0].gradientCheck(_parms._lambda[_lambdaId],_parms._alpha[0]) < GLM_GRAD_EPS) && ++_lambdaId  < (_parms._lambda.length-1));
            Log.info("GLM next lambdaId = " + _lambdaId);
            assert _tInfos[0]._ginfo._gradient.length == _dinfo.fullN()+(_dinfo._intercept?1:0);
            new GLMSingleLambdaTsk(new LambdaSearchIteration((H2OCountedCompleter) getCompleter()),  _tInfos[0]).fork();
          }
        }
      }
    }
  }
  private void setSubmodel(Key dstKey, int iter, double[] fullBeta, GLMValidation trainVal, GLMValidation holdOutVal, H2OCountedCompleter cmp) {
    final double[] newBetaDeNorm;
    final double [] fb = MemoryManager.arrayCopyOf(fullBeta,fullBeta.length);
    if(_parms._intercept)
      fb[fb.length-1] += _iceptAdjust;
    if (_dinfo._predictor_transform == DataInfo.TransformType.STANDARDIZE) {
      newBetaDeNorm = fb.clone();
      double norm = 0.0;        // Reverse any normalization on the intercept
      // denormalize only the numeric coefs (categoricals are not normalized)
      final int numoff = _dinfo.numStart();
      for (int i = numoff; i < fb.length - 1; i++) {
        double b = newBetaDeNorm[i] * _dinfo._normMul[i - numoff];
        norm += b * _dinfo._normSub[i - numoff]; // Also accumulate the intercept adjustment
        newBetaDeNorm[i] = b;
      }
      if(_parms._intercept)
        newBetaDeNorm[newBetaDeNorm.length - 1] -= norm;
    } else {
      newBetaDeNorm = null;
    }
    GLMModel.setSubmodel(cmp, dstKey, _parms._lambda[_lambdaId], newBetaDeNorm == null ? fb : newBetaDeNorm, newBetaDeNorm == null ? null : fb,iter, System.currentTimeMillis() - _start_time, _dinfo.fullN() >= sparseCoefThreshold, trainVal, holdOutVal, _train, _valid);
  }

  double objVal(double likelihood, double[] beta, double lambda, long nobs, boolean intercept) {
    double alpha = _parms._alpha[0];
    double proximalPen = 0;
    if (_bc._betaGiven != null) {
      for (int i = 0; i < _bc._betaGiven.length; ++i) {
        double diff = beta[i] - _bc._betaGiven[i];
        proximalPen += diff * diff * _bc._rho[i] * .5;
      }
    }
    return likelihood / nobs
      + proximalPen
      + lambda * (alpha * ArrayUtils.l1norm(beta, intercept)
      + (1 - alpha) * .5 * ArrayUtils.l2norm2(beta, intercept));
  }

  /**
   * Task to compute GLM solution for a particular (single) lambda value.
   * Can be warm-started by passing in a state of previous computation so e.g. incremental strong rules can be
   * applied.
   *
   * The performs iterative reweighted least squares algorithm with elastic net penalty.
   *
   */
  public final class GLMSingleLambdaTsk extends DTask<GLMSingleLambdaTsk> {
    DataInfo _activeData;
    GLMTaskInfo _taskInfo;

    long _start_time;

    public GLMSingleLambdaTsk(H2OCountedCompleter cmp, GLMTaskInfo state) {
      super(cmp);
      _taskInfo = state;
      assert DKV.get(_dinfo._key) != null;
    }

    private String LogInfo(String msg) {
      msg = "GLM[dest=" + _taskInfo._dstKey + ", iteration=" + _taskInfo._iter + ", lambda = " + _parms._lambda[_lambdaId] + "]: " + msg;
      Log.info(msg);
      return msg;
    }


    boolean _allIn;

    /**
     * Apply strong rules to filter out expected inactive (with zero coefficient) predictors.
     *
     * @return indices of expected active predictors.
     */
    private int[] activeCols(final double l1, final double l2, final double[] grad) {
      if (_allIn) return null;
      int selected = 0;
      int[] cols = null;
      if (_parms._alpha[0] > 0) {
        final double rhs = _parms._alpha[0] * (2 * l1 - l2);
        cols = MemoryManager.malloc4(_dinfo.fullN());
        int j = 0;
        int [] oldActiveCols = _taskInfo._activeCols;
        if (oldActiveCols == null) oldActiveCols = new int[0];
        for (int i = 0; i < _dinfo.fullN(); ++i)
          if ((j < oldActiveCols.length && i == oldActiveCols[j]) || grad[i] > rhs || grad[i] < -rhs) {
            cols[selected++] = i;
            if (j < oldActiveCols.length && i == oldActiveCols[j]) ++j;
          }
      }
      if (_parms._alpha[0] == 0 || selected == _dinfo.fullN()) {
        _allIn = true;
        _activeData = _dinfo;
        LogInfo("strong rule at lambda_value=" + l1 + ", all " + _dinfo.fullN() + " coefficients are active");
        return null;
      } else {
        LogInfo("strong rule at lambda_value=" + l1 + ", got " + selected + " active cols out of " + _dinfo.fullN() + " total.");
        return Arrays.copyOf(cols, selected);
      }
    }

//    private transient IterationInfo _lastResult;

    private double[] setSubmodel(final double[] newBeta, GLMValidation trainVal, GLMValidation holdOutVal, H2OCountedCompleter cmp) {
      if(trainVal != null) // kind of ugly, set intercept flags on validation objects here
        trainVal._intercept = _parms._intercept;
      if(holdOutVal != null)
        holdOutVal._intercept = _parms._intercept;
      double[] fullBeta = (_taskInfo._activeCols == null || newBeta == null) ? newBeta : expandVec(newBeta, _taskInfo._activeCols, _dinfo.fullN() + 1);
      if (fullBeta == null) {
        fullBeta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        if(_parms._intercept)
          fullBeta[fullBeta.length - 1] = _parms.linkInv(_taskInfo._ymu);
      }
      GLM.this.setSubmodel(_taskInfo._dstKey, _taskInfo._iter, fullBeta, trainVal, holdOutVal, cmp);
      return fullBeta;
    }

    protected void solve(){
      if (_activeData.fullN() > _parms._max_active_predictors)
        throw new TooManyPredictorsException();
      Solver solverType = _parms._solver;
      if(solverType == Solver.AUTO) {
        if(_activeData.fullN() > 6000 || _activeData._adaptedFrame.numCols() > 500)
          solverType = Solver.L_BFGS;
        else {
          solverType = Solver.IRLSM; // default choice
        }
      }
      switch(solverType) {
        case L_BFGS: {
          double[] beta = _taskInfo._beta;
          assert beta.length == _activeData.fullN()+1;
          GradientSolver solver = new GLMGradientSolver(_parms, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _taskInfo._ymu, _taskInfo._nobs, _rowFilter);
          if(_bc._betaGiven != null && _bc._rho != null)
            solver = new ProximalGradientSolver(solver,_bc._betaGiven,_bc._rho);
          if (beta == null) {
            beta = MemoryManager.malloc8d(_activeData.fullN() + (_activeData._intercept ? 1 : 0));
            if (_activeData._intercept)
              beta[beta.length - 1] = _parms.link(_taskInfo._ymu);
          }
          L_BFGS lbfgs = new L_BFGS().setMaxIter(_parms._max_iterations);
          assert beta.length == _taskInfo._ginfo._gradient.length;
          double l1pen = _parms._lambda[_lambdaId] * _parms._alpha[0];
          if(l1pen > 0 || _bc.hasBounds()) {
            // compute gradient at null beta to get estimate for rho
            double [] nullBeta = MemoryManager.malloc8d(_taskInfo._beta.length);
            if(_dinfo._intercept)
              nullBeta[nullBeta.length-1] = _parms.link(_taskInfo._ymu);
            double [] g = solver.getGradient(nullBeta)._gradient;
            double [] rho = MemoryManager.malloc8d(beta.length);
            // compute rhos
            for(int i = 0; i < rho.length - (_dinfo._intercept?1:0); ++i)
              rho[i] = ADMM.L1Solver.estimateRho(-g[i], l1pen);
            new ADMM.L1Solver(1e-4, 1000).solve(new LBFGS_ProximalSolver(solver,_taskInfo._beta,rho, GLM.this._key), _taskInfo._beta, l1pen);
          } else {
            Result r = lbfgs.solve(solver, beta, _taskInfo._ginfo, new ProgressMonitor() {
              @Override
              public boolean progress(double[] beta, GradientInfo ginfo) {
                if(ginfo instanceof GLMGradientInfo) {
                  GLMGradientInfo gginfo = (GLMGradientInfo) ginfo;
                  _scoring_iters.add(_taskInfo._iter);
                  _likelihoods.add(gginfo._likelihood);
                  _objectives.add(gginfo._objVal);
                }
                if ((_taskInfo._iter & 7) == 0) {
                  _taskInfo._worked += _taskInfo._workPerIteration*8;
                  update(_taskInfo._workPerIteration*8, "iteration " + (_taskInfo._iter + 1) + ", objective value = " + ginfo._objVal + ", gradient norm = " + ArrayUtils.l2norm2(ginfo._gradient,false), GLM.this._key);
                  LogInfo("LBFGS: objval = " + ginfo._objVal);
                }
                ++_taskInfo._iter;
                // todo update the model here so we can show intermediate results
                return isRunning(GLM.this._key);
              }
            });
            _taskInfo._beta = r.coefs;
          }
          break;
        }
        case COORDINATE_DESCENT:
          double l1pen = _parms._alpha[0]*_parms._lambda[_lambdaId];
          double l2pen = (1-_parms._alpha[0])*_parms._lambda[_lambdaId];
          double [] beta = _taskInfo._beta.clone();
          int off;
          double xOldSub;
          double xOldMul;
          double xNewSub = 0;
          double xNewMul = 1;
          double [] betaUpdate = null;
          boolean betaChanges = true;
          int iter = 0;
          // external loop - each time generate weights based on previous beta, compute new beta as solution to weighted least squares
          while(betaChanges) {
            // internal loop - go over each column independently as long as beta keeps changing
            int it = iter; // to keep track of inner iterations
            while (betaChanges && ++iter < 1000) {
              betaChanges = false;
              // run one iteration of coordinate descent - go over all columns
              for (int i = 0; i < _activeData._adaptedFrame.numCols(); ++i) {
                Vec previousVec = i == 0?_taskInfo._iVec:_dinfo._adaptedFrame.vec(i-1);
                Vec currentVec = i == _dinfo._adaptedFrame.numCols()-1?_taskInfo._iVec:_dinfo._adaptedFrame.vec(i);
                xOldSub = xNewSub;
                xOldMul = xNewMul;
                boolean isCategorical = currentVec.isEnum();
                int to;
                if (isCategorical) {
                  xNewSub = 0;
                  xNewMul = 1;
                  off = _dinfo._catOffsets[i];
                  to = _dinfo._catOffsets[i + 1];
                } else {
                  int k = i - _dinfo._cats;
                  xNewSub = _dinfo._normSub[k];
                  xNewMul = _dinfo._normMul[k];
                  off = _dinfo.numStart() + k;
                  to = off + 1;
                }
                double[] currentBeta = Arrays.copyOfRange(_taskInfo._beta, off, to);
                double[] xy = new GLMCoordinateDescentTask(betaUpdate, currentBeta, xOldSub, xOldMul, xNewSub, xNewMul).doAll(previousVec,currentVec,_taskInfo._eVec,_taskInfo._wVec, _taskInfo._zVec)._xy;
                for (int j = 0; j < xy.length; ++j) {
                  betaUpdate = currentBeta;
                  double updatedCoef = ADMM.shrinkage(xy[j], l1pen) / (1 + l2pen);
                  betaUpdate[j] = updatedCoef - currentBeta[j];
                  if (betaUpdate[j] < -1e-4 || betaUpdate[j] > 1e-4)
                    betaChanges = true;
                  beta[off + j] = updatedCoef;
                }
              }
            }
            if(iter > it+1) {
              betaChanges = true; // beta changed during inner iteration
              // generate new weights
              new GLMTask.GLMWeightsTask(_parms).doAll(_dinfo._adaptedFrame.lastVec(), _taskInfo._zVec, _taskInfo._wVec, _taskInfo._eVec);
            }
          }
          // done, compute the gradient and check KKTs
          break;
        case IRLSM:// fork off ADMM iteration
          new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, false, _taskInfo._beta, _parms._intercept?_taskInfo._ymu:0.5, _rowFilter, new Iteration(this, false)).asyncExec(_activeData._adaptedFrame);
          return;
        default:
          throw H2O.unimpl();
      }
      checkKKTsAndComplete();
      tryComplete();
    }
    // Compute full gradient gradient (including inactive columns) and check KKT conditions, re-solve if necessary.
    // Can't be onCompletion(), can invoke solve again
    protected void checkKKTsAndComplete() {
      final double [] fullBeta = expandVec(_taskInfo._beta,_activeData._activeCols,_dinfo.fullN()+1);
      addToPendingCount(1);
      new GLMTask.GLMGradientTask(_dinfo, _parms, _parms._lambda[_lambdaId], fullBeta, 1.0 / _taskInfo._nobs, _rowFilter, new H2OCallback<GLMGradientTask>(this) {
        @Override
        public void callback(final GLMGradientTask gt1) {
          assert gt1._nobs == _taskInfo._nobs;
          double[] subgrad = gt1._gradient.clone();
          ADMM.subgrad(_parms._alpha[0] * _parms._lambda[_lambdaId], fullBeta, subgrad);
          double err = GLM_GRAD_EPS;
          if (_taskInfo._activeCols != null) {
            for (int c : _taskInfo._activeCols)
              if (subgrad[c] > err) err = subgrad[c];
              else if (subgrad[c] < -err) err = -subgrad[c];
            LogInfo("solved with gerr = " + err);
            int[] failedCols = new int[64];
            int fcnt = 0;
            for (int i = 0; i < subgrad.length - 1; ++i) {
              if (Arrays.binarySearch(_taskInfo._activeCols, i) >= 0) continue;
              if (subgrad[i] > err || -subgrad[i] > err) {
                if (fcnt == failedCols.length)
                  failedCols = Arrays.copyOf(failedCols, failedCols.length << 1);
                failedCols[fcnt++] = i;
              }
            }
            if (fcnt > 0) {
              final int n = _taskInfo._activeCols.length;
              int[] newCols = Arrays.copyOf(_taskInfo._activeCols, _taskInfo._activeCols.length + fcnt);
              for (int i = 0; i < fcnt; ++i)
                newCols[n + i] = failedCols[i];
              Arrays.sort(newCols);
              _taskInfo._beta = resizeVec(gt1._beta, newCols, _taskInfo._activeCols, _dinfo.fullN() + 1);
              _taskInfo._activeCols = newCols;
              LogInfo(fcnt + " variables failed KKT conditions check! Adding them to the model and continuing computation.(grad_eps = " + err + ", activeCols = " + (_taskInfo._activeCols.length > 100 ? "lost" : Arrays.toString(_taskInfo._activeCols)));
              _activeData = _dinfo.filterExpandedColumns(_taskInfo._activeCols);
              assert newCols == null || _activeData.fullN() == _taskInfo._activeCols.length;
              // NOTE: tricky completer game here:
              // We expect 0 pending in this method since this is the end-point, ( actually it's racy, can be 1 with pending 1 decrement from the original Iteration callback, end result is 0 though)
              // while iteration expects pending count of 1, so we need to increase it here (Iteration itself adds 1 but 1 will be subtracted when we leave this method since we're in the callback which is called by onCompletion!
              // [unlike at the start of nextLambda call when we're not inside onCompletion]))
              getCompleter().addToPendingCount(1);
              solve();
              return;
            }
          }
          if (_valid != null) {
            GLMSingleLambdaTsk.this.addToPendingCount(1);
            // public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, H2OCountedCompleter cc){
            new GLMTask.GLMGradientTask(_dinfo, _parms, _parms._lambda[_lambdaId], gt1._beta, 1.0 / _taskInfo._nobs, null /* no rowf filter for validation dataset */, new H2OCallback<GLMGradientTask>(GLMSingleLambdaTsk.this) {
              @Override
              public void callback(GLMGradientTask gt2) {
                LogInfo("hold-out set validation: \n" + gt2._val.toString());
                setSubmodel(_taskInfo._beta, gt1._val, gt2._val, GLMSingleLambdaTsk.this);
              }
            }).setValidate(_parms._intercept?_taskInfo._ymu:0, true).asyncExec(_validDinfo._adaptedFrame);
          } else {
            setSubmodel(_taskInfo._beta, gt1._val, null, null);
          }
          // got valid solution, update the state and complete
          double l2pen = _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]) * ArrayUtils.l2norm2(gt1._beta, _activeData._intercept);
          if(_bc._betaGiven != null && _bc._rho != null) {
            for(int i = 0; i < _bc._betaGiven.length; ++i) {
              double diff = gt1._beta[i] - _bc._betaGiven[i];
              l2pen += _bc._rho[i] * diff * diff;
            }
          }
          l2pen *= .5;
          _taskInfo._ginfo = new GLMGradientInfo(gt1._likelihood, gt1._likelihood/gt1._nobs + l2pen, gt1._gradient);
          assert _taskInfo._ginfo._gradient.length == _dinfo.fullN() + 1:_taskInfo._ginfo._gradient.length + " != " + _dinfo.fullN() + ", intercept = " + _parms._intercept;
          _taskInfo._objVal = objVal(gt1._likelihood,gt1._beta, _parms._lambda[_lambdaId],gt1._nobs,_dinfo._intercept);
          _taskInfo._beta = fullBeta;
        }
      }).setValidate(_parms._intercept?_taskInfo._ymu:0.5,true).asyncExec(_dinfo._adaptedFrame);
    }
    @Override
    protected void compute2() {
      if(!isRunning(_key)) throw new JobCancelledException();
      assert _rowFilter != null;
      _start_time = System.currentTimeMillis();
      LogInfo("lambda = " + _parms._lambda[_lambdaId] + "\n");
      int[] activeCols = activeCols(_parms._lambda[_lambdaId], _lambdaId == 0?_taskInfo._lambdaMax:_parms._lambda[_lambdaId-1], _taskInfo._ginfo._gradient);
      _taskInfo._activeCols = activeCols;
      _activeData = _dinfo.filterExpandedColumns(activeCols);
      assert _taskInfo._activeCols == null || _taskInfo._activeCols.length == _activeData.fullN();
      _taskInfo._ginfo = new GLMGradientInfo(_taskInfo._ginfo._likelihood, _taskInfo._ginfo._objVal,contractVec(_taskInfo._ginfo._gradient,activeCols));
      _taskInfo._beta = contractVec(_taskInfo._beta,activeCols);
      assert  activeCols == null || _activeData.fullN() == activeCols.length : LogInfo("mismatched number of cols, got " + activeCols.length + " active cols, but data info claims " + _activeData.fullN());
      assert DKV.get(_activeData._key) != null;
      solve();
    }
    private class Iteration extends H2O.H2OCallback<GLMIterationTask> {
      public final long _iterationStartTime;
      final boolean _countIteration;
      public final boolean _doLinesearch;

      public Iteration(CountedCompleter cmp, boolean doLinesearch) {
        this(cmp, doLinesearch, true);
      }

      public Iteration(CountedCompleter cmp, boolean doLinesearch, boolean countIteration) {
        super((H2OCountedCompleter) cmp);
        _countIteration = countIteration;
        _iterationStartTime = System.currentTimeMillis();
        _doLinesearch = doLinesearch;
      }

      @Override
      public void callback(final GLMIterationTask glmt) {
        assert _parms._intercept || glmt._beta[_activeData.fullN()] == 0;
        double objVal = objVal(glmt._likelihood, glmt._beta, _parms._lambda[_lambdaId], _taskInfo._nobs, _activeData._intercept);
        if (!isRunning(GLM.this._key)) throw new JobCancelledException();
        assert glmt._nobs == _taskInfo._nobs:"got wrong number of observations, expected " + _taskInfo._nobs + ", but got " + glmt._nobs + ", got row filter?" + (glmt._rowFilter != null);
        assert _taskInfo._activeCols == null || glmt._beta == null || glmt._beta.length == (_taskInfo._activeCols.length + 1) : LogInfo("betalen = " + glmt._beta.length + ", activecols = " + _taskInfo._activeCols.length);
        assert _taskInfo._activeCols == null || _taskInfo._activeCols.length == _activeData.fullN();
        double reg = 1.0 / _taskInfo._nobs;
        glmt._gram.mul(reg);
        ArrayUtils.mult(glmt._xy, reg);
        if (_countIteration) ++_taskInfo._iter;
        long callbackStart = System.currentTimeMillis();
        double lastObjVal = _taskInfo._objVal;
        LogInfo("gram computed in " + (callbackStart - _iterationStartTime) + "ms");
        double logl = glmt._likelihood;
        LogInfo("-log(l) = " + logl + ", obj = " + objVal);

        if (_doLinesearch && (glmt.hasNaNsOrInf() || !(lastObjVal > objVal))) {
          getCompleter().addToPendingCount(1);
          LogInfo("invoking line search, objval = " + objVal + ", lastObjVal = " + lastObjVal); // todo: get gradient here?
          new GLMLineSearchTask(_activeData, _parms, 1.0 / _taskInfo._nobs, _taskInfo._beta.clone(), ArrayUtils.subtract(glmt._beta, _taskInfo._beta), LINE_SEARCH_STEP, NUM_LINE_SEARCH_STEPS, _rowFilter, new LineSearchIteration(getCompleter())).asyncExec(_activeData._adaptedFrame);
          return;
        } else if (lastObjVal > objVal) {
          ++_taskInfo._iter; // =new IterationInfo(_iter, glmt._beta, glmt._objVal);
          _taskInfo._beta = glmt._beta;
          _taskInfo._objVal = objVal;
          _taskInfo._ginfo = null;
          _scoring_iters.add(_taskInfo._iter);
          _likelihoods.add(logl);
          _objectives.add(objVal);
        }
        final double[] newBeta = MemoryManager.malloc8d(glmt._xy.length);
        double l2pen = _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]);
        double l1pen = _parms._lambda[_lambdaId] * _parms._alpha[0];
        double defaultRho = _bc._betaLB != null || _bc._betaUB != null ? _taskInfo._lambdaMax * 1e-2 : 0;
        // l1pen or upper/lower bounds require ADMM solver
        if (l1pen > 0 || _bc._betaLB != null || _bc._betaUB != null || _bc._betaGiven != null) {
          // double rho = Math.max(1e-4*_taskInfo._lambdaMax*_parms._alpha[0],_currentLambda*_parms._alpha[0]);
          long tx = System.currentTimeMillis();
          GramSolver gslvr = new GramSolver(glmt._gram, glmt._xy, _activeData._intercept, l2pen, l1pen /*, rho*/, _bc._betaGiven, _bc._rho, defaultRho, _bc._betaLB, _bc._betaUB);
          long ty = System.currentTimeMillis();
          new ADMM.L1Solver(1e-4, 1000).solve(gslvr, newBeta, l1pen, _activeData._intercept, _bc._betaLB, _bc._betaUB);
          LogInfo("ADMM done in " + (System.currentTimeMillis() - tx) + "ms, cholesky took " + (ty - tx) + "ms");
        } else {
          glmt._gram.addDiag(l2pen);
          new GramSolver(glmt._gram,glmt._xy,_taskInfo._lambdaMax, _parms._beta_epsilon, _parms._intercept).solve(newBeta);
        }
        _taskInfo._worked += _taskInfo._workPerIteration;
        update(_taskInfo._workPerIteration, "lambdaId = " + _lambdaId + ", iteration = " + _taskInfo._iter + ", objective value = " + objVal);
        if (ArrayUtils.hasNaNsOrInfs(newBeta)) {
          throw new RuntimeException(LogInfo("got NaNs and/or Infs in beta"));
        } else {
          final double bdiff = beta_diff(glmt._beta, newBeta);
          if ((_parms._family == Family.gaussian && _parms._link == Link.identity) || bdiff < _parms._beta_epsilon || _taskInfo._iter >= _parms._max_iterations) { // Gaussian is non-iterative and gradient is ADMMSolver's gradient => just validate and move on to the next lambda_value
            int diff = (int) Math.log10(bdiff);
            int nzs = 0;
            for (int i = 0; i < newBeta.length; ++i)
              if (newBeta[i] != 0) ++nzs;
            LogInfo("converged (reached a fixed point with ~ 1e" + diff + " precision), got " + nzs + " nzs");
            _taskInfo._beta = _parms._family == Family.gaussian ? newBeta : glmt._beta;
            checkKKTsAndComplete();
            return;
          } else { // not done yet, launch next iteration
            if (glmt._beta != null) {
              setSubmodel(glmt._beta, glmt._val, null, (H2OCountedCompleter) getCompleter().getCompleter()); // update current intermediate result
            }
            final boolean validate = (_taskInfo._iter % 5) == 0;
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), glmt._glm, validate, newBeta, _parms._intercept?_taskInfo._ymu:0.5, _rowFilter,new Iteration(getCompleter(), true)).asyncExec(_activeData._adaptedFrame);
          }
        }
      }
    }

    private class LineSearchIteration extends H2O.H2OCallback<GLMLineSearchTask> {
      LineSearchIteration(CountedCompleter cmp) {
        super((H2OCountedCompleter) cmp);
      }

      @Override
      public void callback(final GLMLineSearchTask lst) {
        assert lst._nobs == _taskInfo._nobs:lst._nobs + " != " + _taskInfo._nobs  + ", filtervec = " + (lst._rowFilter == null);
        double t = 1;
        for (int i = 0; i < lst._likelihoods.length; ++i, t *= LINE_SEARCH_STEP) {
          double[] beta = ArrayUtils.wadd(_taskInfo._beta.clone(), lst._direction, t);
          double newObj = objVal(lst._likelihoods[i], beta, _parms._lambda[_lambdaId],_taskInfo._nobs,_activeData._intercept);
          if (_taskInfo._objVal > newObj) {
            assert t < 1;
            LogInfo("line search: found admissible step = " + t + ",  objval = " + newObj);
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, true, beta, _parms._intercept?_taskInfo._ymu:.5, _rowFilter, new Iteration(getCompleter(), true, false)).asyncExec(_activeData._adaptedFrame);
            return;
          }
        }
        // no line step worked => converge
        LogInfo("Line search did not find feasible step, converged at objval = " + _taskInfo._objVal);
        checkKKTsAndComplete();
      }
    }

    private final double beta_diff(double[] b1, double[] b2) {
      if (b1 == null) return Double.MAX_VALUE;
      double res = b1[0] >= b2[0] ? b1[0] - b2[0] : b2[0] - b1[0];
      for (int i = 1; i < b1.length; ++i) {
        double diff = b1[i] - b2[i];
        if (diff > res)
          res = diff;
        else if (-diff > res)
          res = -diff;
      }
      return res;
    }


    protected double l1norm(double[] beta) {
      if (beta == null) return 0;
      double l1 = 0;
      for (int i = 0; i < beta.length - 1; ++i)
        l1 += beta[i] < 0 ? -beta[i] : beta[i];
      return l1;
    }
  }

  /**
   * Created by tomasnykodym on 3/30/15.
   */
  public static final class GramSolver implements ProximalSolver {
    private final Gram _gram;
    private Cholesky _chol;

    private final double [] _xy;
    final double _lambda;
    double  [] _rho;
    boolean _addedL2;
    double _betaEps;

    private static double boundedX(double x, double lb, double ub) {
      if(x < lb)x = lb;
      if(x > ub)x = ub;
      return x;
    }

    public GramSolver(Gram gram, double[] xy, double lmax, double betaEps, boolean intercept) {
      _gram = gram;
      _lambda = 0;
      _betaEps = betaEps;
      if(!intercept) {
        gram.dropIntercept();
        xy = Arrays.copyOf(xy,xy.length-1);
      }
      _xy = xy;
      double [] rhos = MemoryManager.malloc8d(xy.length);
      computeCholesky(gram,rhos, lmax*1e-8);
      _addedL2 = rhos[0] != 0;
      _rho = _addedL2?rhos:null;
    }
    // solve non-penalized problem
    public void solve(double [] result) {
      System.arraycopy(_xy,0,result,0, _xy.length);
      _chol.solve(result);
      double gerr = Double.POSITIVE_INFINITY;
      if(_addedL2) { // had to add l2-pen to turn the gram to be SPD
        double [] oldRes = MemoryManager.arrayCopyOf(result, result.length);
        for(int i = 0; i < 1000; ++i) {
          solve(oldRes, result);
          double [] g = gradient(result);
          gerr = Math.max(-ArrayUtils.minValue(g), ArrayUtils.maxValue(g));
          if(gerr < 1e-4) return;
          System.arraycopy(result,0,oldRes,0,result.length);
        }
        Log.warn("Gram solver did not converge, gerr = " + gerr);
      }
    }

    public GramSolver(Gram gram, double[] xy, boolean intercept, double l2pen, double l1pen, double[] beta_given, double[] proxPen, double default_rho, double[] lb, double[] ub) {
      if(ub != null && lb != null)
        for(int i = 0; i < ub.length; ++i) {
          assert ub[i] >= lb[i]:i + ": ub < lb, ub = " + Arrays.toString(ub) + ", lb = " + Arrays.toString(lb) ;
        }
      _lambda = l2pen;
      _gram = gram;
      // Try to pick optimal rho constant here used in ADMM solver.
      //
      // Rho defines the strength of proximal-penalty and also the strentg of L1 penalty aplpied in each step.
      // Picking good rho constant is tricky and greatly influences the speed of convergence and precision with which we are able to solve the problem.
      //
      // Intuitively, we want the proximal l2-penalty ~ l1 penalty (l1 pen = lambda/rho, where lambda is the l1 penalty applied to the problem)
      // Here we compute the rho for each coordinate by using equation for computing coefficient for single coordinate and then making the two penalties equal.
      //
      int ii = intercept?1:0;
      int icptCol = xy.length-1;
      double [] rhos = MemoryManager.malloc8d(xy.length - 1 + ii);
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < xy.length - 1; ++i) {
        double d = xy[i];
        d = d >= 0 ? d : -d;
        if (d < min && d != 0) min = d;
      }
      double ybar = xy[icptCol];
      for (int i = 0; i < rhos.length - ii; ++i) {
        double y = xy[i];
        if (y == 0) y = min;
        double xbar = gram.get(icptCol, i);
        double x = (beta_given != null && proxPen != null)
          ? (y - ybar * gram.get(icptCol, i) + proxPen[i] * beta_given[i]) / ((gram.get(i, i) - xbar * xbar) + l2pen + proxPen[i])
          : ((y - ybar * xbar) / (gram.get(i, i) - xbar * xbar) + l2pen);///gram.get(i,i);
        double rho = ADMM.L1Solver.estimateRho(x,l1pen);
        // upper nad lower bounds have different rho requirements.
        if(ub != null && !Double.isInfinite(ub[i]) || lb != null && !Double.isInfinite(lb[i])) {
          double lx = (x - lb[i]);
          double ux = (ub[i] - x);
          double xx = Math.min(lx,ux);
          rhos[i] = Math.max(rho,xx <= .5*x?1:1e-4);
        } else {
          rhos[i] = rho; // Math.min(avg*1e2,Math.max(avg*1e-2,y));
        }
      }
      // do the intercept separate as l1pen does not apply to it
      if (intercept && (lb != null && !Double.isInfinite(lb[icptCol]) || ub != null && !Double.isInfinite(ub[icptCol]))) {
        int icpt = xy.length - 1;
        rhos[icpt] = 1;//(xy[icpt] >= 0 ? xy[icpt] : -xy[icpt]);
      }
      if(!intercept) {
        gram.dropIntercept();
        xy = Arrays.copyOf(xy,xy.length-1);
      }
      if(l2pen > 0)
        gram.addDiag(l2pen);
      if(proxPen != null && beta_given != null) {
        gram.addDiag(proxPen);
        xy = xy.clone();
        for(int i = 0; i < xy.length; ++i)
          xy[i] += proxPen[i]*beta_given[i];
      }
      computeCholesky(gram,rhos,1e-5);
      _rho = rhos;
      _xy = xy;
    }

    private void computeCholesky(Gram gram, double [] rhos, double rhoAdd) {
      gram.addDiag(rhos);
      _chol = gram.cholesky(null,true,null);
      if(!_chol.isSPD())  { // make sure rho is big enough
        gram.addDiag(ArrayUtils.mult(rhos, -1));
        ArrayUtils.mult(rhos, -1);
        for(int i = 0; i < rhos.length; ++i)
          rhos[i] += rhoAdd;//1e-5;
        Log.info("Got NonSPD matrix with original rho, re-computing with rho = " + rhos[0]);
        _gram.addDiag(rhos);
        _chol = gram.cholesky(null,true,null);
        int cnt = 0;
        while(!_chol.isSPD() && cnt++ < 5) {
          gram.addDiag(ArrayUtils.mult(rhos, -1));
          ArrayUtils.mult(rhos, -1);
          for(int i = 0; i < rhos.length; ++i)
            rhos[i] *= 100;
          Log.warn("Still NonSPD matrix, re-computing with rho = " + rhos[0]);
            _gram.addDiag(rhos);
          _chol = gram.cholesky(null,true,null);
        }
        if(!_chol.isSPD())
          throw new NonSPDMatrixException();
      }
      gram.addDiag(ArrayUtils.mult(rhos, -1));
      ArrayUtils.mult(rhos, -1);
    }

    @Override
    public double [] rho() { return _rho;}

    @Override
    public void solve(double[] beta_given, double[] result) {
      if(beta_given != null)
        for(int i = 0; i < _xy.length; ++i)
          result[i] = _xy[i] + _rho[i] * beta_given[i];
      else
        System.arraycopy(_xy,0,result,0,_xy.length);
      _chol.solve(result);
    }

    @Override
    public boolean hasGradient() { return false;}

    @Override
    public double[] gradient(double [] beta) {
      double [] grad = _gram.mul(beta);
      for(int i = 0; i < grad.length; ++i)
        grad[i] -= _xy[i];
      return grad;
    }

    @Override public void setRho(double [] r){throw new UnsupportedOperationException(); /* could do it but it's (very) expensive, so throw UOE instead */}
    @Override
    public boolean canSetRho() { return false; }

    @Override
    public int iter() {
      return 0;
    }
  }


  public static final class LBFGS_ProximalSolver implements ProximalSolver {
    double [] _beta;
    final double [] _rho;
    final GradientSolver _gSolver;
    double [] _gradient;
    public int _iter;
    final Key _jobKey;

    public LBFGS_ProximalSolver(GradientSolver gs, double [] beta, double [] rho, Key jobKey){
      _gSolver = gs;
      _beta = beta;
      _rho = rho;
      _jobKey = jobKey;
    }

    @Override
    public double[] rho() { return _rho;}

    double [] _beta_given;
    GradientInfo _ginfo;
    @Override
    public void solve(double[] beta_given, double[] result) {
      if(_jobKey != null && !Job.isRunning(_jobKey))
        throw new JobCancelledException();
      ProximalGradientSolver s = new ProximalGradientSolver(_gSolver,beta_given,_rho);
      if(_beta_given == null)
        _beta_given = MemoryManager.malloc8d(beta_given.length);
      if(_ginfo != null) { // update the gradient
        for(int i = 0; i < beta_given.length; ++i) {
          _ginfo._gradient[i] += _rho[i] * (_beta_given[i] - beta_given[i]);
          _ginfo._objVal += .5 * _rho[i] *  (((result[i] - beta_given[i]) * (result[i] - beta_given[i])) -( (result[i] - _beta_given[i]) * (result[i] - _beta_given[i])));
          _beta_given[i] = beta_given[i];
        }
      } else _ginfo = s.getGradient(result);

      L_BFGS.Result r  = new L_BFGS().solve(s, result.clone(), _ginfo, new ProgressMonitor() {
        public boolean progress(double[] beta, GradientInfo ginfo) {
          return _jobKey == null || Job.isRunning(_jobKey);
        }
      });
      _ginfo = r.ginfo;
      _beta = r.coefs;
      _gradient = r.ginfo._gradient;
      _iter += r.iter;
      System.arraycopy(_beta,0,result,0,_beta.length);
    }

    @Override
    public boolean hasGradient() {
      return _gradient != null;
    }

    @Override
    public double[] gradient(double[] beta) { return _gSolver.getGradient(beta)._gradient;}

    @Override
    public void setRho(double[] rho) {
      System.arraycopy(rho,0,_rho,0,_rho.length);
    }

    @Override
    public boolean canSetRho() { return true;}

    @Override
    public int iter() {
      return _iter;
    }
  }

  /**
   * Simple wrapper around gradient computation, adding proximal penalty
   */
  public static class ProximalGradientSolver extends GradientSolver {
    final GradientSolver _solver;
    final double [] _betaGiven;
    final double [] _rho;

    public ProximalGradientSolver(GradientSolver s, double [] betaGiven, double [] rho) {
      _solver = s;
      _betaGiven = betaGiven;
      _rho = rho;
    }
    @Override
    public GradientInfo getGradient(double[] beta) {
      GradientInfo gt = _solver.getGradient(beta);
      for (int i = 0; i < gt._gradient.length; ++i) {
        double diff = (beta[i] - _betaGiven[i]);
        double pen = _rho[i] * diff;
        gt._gradient[i] += pen;
        gt._objVal += .5*pen*diff;
      }
      return gt;
    }
    @Override
    public double[] getObjVals(double[] beta, double[] pk, int nSteps, double stepDec) {
      double [] objs = _solver.getObjVals(beta,pk, nSteps, stepDec);
      double step = 1;
      assert objs.length == nSteps;
      for (int i = 0; i < objs.length; ++i, step *= stepDec) {
        double [] b = ArrayUtils.wadd(beta.clone(), pk, step);
        double pen = 0;
        for (int j = 0; j < _betaGiven.length; ++j) {
          double diff = b[j] - _betaGiven[j];
          pen +=  _rho[j] * diff * diff;
        }
        objs[i] += .5 * pen;
      }
      return objs;
    }
  }

  public static final class GLMGradientInfo extends L_BFGS.GradientInfo {
    final double _likelihood;
    public GLMGradientInfo(double likelihood, double objVal, double[] grad) {
      super(objVal, grad);
      _likelihood = likelihood;
    }
  }
  /**
   * Gradient and line search computation for L_BFGS and also L_BFGS solver wrapper (for ADMM)
   */
  public static final class GLMGradientSolver extends GradientSolver  {
    final GLMParameters _glmp;
    final DataInfo _dinfo;
    final double _ymu;
    final double _lambda;
    final long _nobs;
    Vec _rowFilter;
    double [] _beta;

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, long nobs) {
      this(glmp, dinfo, lambda, ymu, nobs, null);
    }

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, long nobs, Vec rowFilter) {
      _glmp = glmp;
      _dinfo = dinfo;
      _ymu = ymu;
      _nobs = nobs;
      _lambda = lambda;
      _rowFilter = rowFilter;
    }

    public GLMGradientSolver setBetaStart(double [] beta) {
      _beta = beta.clone();
      return this;
    }

    @Override
    public GLMGradientInfo getGradient(double[] beta) {
      GLMGradientTask gt = _glmp._family == Family.binomial
        ? new LBFGS_LogisticGradientTask(_dinfo, _glmp, _lambda, beta, 1.0 / _nobs, _rowFilter ).doAll(_dinfo._adaptedFrame)
        :
      /*GLMGradientTask gt = */new GLMGradientTask(_dinfo, _glmp, _lambda, beta, 1.0 / _nobs, _rowFilter).doAll(_dinfo._adaptedFrame);
      return new GLMGradientInfo(gt._likelihood, gt._likelihood/gt._nobs + .5 * _lambda * ArrayUtils.l2norm2(beta,_dinfo._intercept), gt._gradient);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] direction, int nSteps, double stepDec) {
      double reg = 1.0 / _nobs;
      double[] objs = new GLMLineSearchTask(_dinfo, _glmp, 1.0 / _nobs, beta, direction, stepDec, nSteps, _rowFilter).setFasterMetrics(true).doAll(_dinfo._adaptedFrame)._likelihoods;
      double step = 1;
      for (int i = 0; i < objs.length; ++i, step *= stepDec) {
        objs[i] *= reg;
        if (_lambda > 0 ) { // have some l2 pen
          double[] b = ArrayUtils.wadd(beta.clone(), direction, step);
          if (_lambda > 0)
            objs[i] += .5 * _lambda * ArrayUtils.l2norm2(b, _dinfo._intercept);
        }
      }
      return objs;
    }
  }

  private static final double[] expandVec(double[] beta, final int[] activeCols, int fullN) {
    assert beta != null;
    if (activeCols == null) return beta;
    double[] res = MemoryManager.malloc8d(fullN);
    int i = 0;
    for (int c : activeCols)
      res[c] = beta[i++];
    res[res.length - 1] = beta[beta.length - 1];
    return res;
  }

  private final static double[] contractVec(double[] beta, final int[] activeCols) {
    if (beta == null) return null;
    if (activeCols == null) return beta.clone();
    double[] res = MemoryManager.malloc8d(activeCols.length + 1);
    int i = 0;
    for (int c : activeCols)
      res[i++] = beta[c];
    res[res.length - 1] = beta[beta.length - 1];
    return res;
  }
  private final static double[] resizeVec(double[] beta, final int[] activeCols, final int[] oldActiveCols, int fullN) {
    if (beta == null || Arrays.equals(activeCols, oldActiveCols)) return beta;
    double[] full = expandVec(beta, oldActiveCols, fullN);
    if (activeCols == null) return full;
    return contractVec(full, activeCols);
  }

}
