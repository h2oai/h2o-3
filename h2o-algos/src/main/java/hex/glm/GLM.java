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
import hex.optimization.ADMM;
import hex.optimization.ADMM.ProximalSolver;
import hex.optimization.L_BFGS;
import hex.optimization.L_BFGS.*;
import hex.schemas.GLMV2;
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

import java.util.Arrays;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 * TODO: GLM will use a threshold during predict to do binomial classification, but
 * GLMModel currently never returns Binomial as its ModelCategory.
 */
public class GLM extends SupervisedModelBuilder<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  static final double LINE_SEARCH_STEP = .5;
  static final int NUM_LINE_SEARCH_STEPS = 16;
  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
            Model.ModelCategory.Regression,
            // Model.ModelCategory.Binomial, // see TODO comment above.
    };
  }

  public GLM(Key dest, String desc, GLMModel.GLMParameters parms) { super(dest, desc, parms); init(false); }
  public GLM(GLMModel.GLMParameters parms) { super("GLM", parms); init(false); }

  static class TooManyPredictorsException extends RuntimeException {}

  private BetaConstraint _bc = new BetaConstraint();
  private GLMValidation _nullValidation;
  private GradientInfo _nullGradient;
  DataInfo _dinfo;
  private Vec _rowFilter;
  private transient GLMTaskInfo [] _tInfos;
  private int _lambdaId;
  private transient DataInfo _validDinfo;

  @Override public void init(boolean expensive) {
    super.init(expensive);
    _parms.validate(this);
    if (expensive) {
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
      if (_parms._beta_constraint != null) {
        Frame beta_constraints = _parms._beta_constraint.get();
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
          if (betaStart == null)
            betaStart = betaGiven;
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
            double dd = _dinfo._normMul[i - numoff];
            double d = 1.0 / dd;
            if (betaUB != null && !Double.isInfinite(betaUB[i]))
              betaUB[i] *= d;
            if (betaLB != null && !Double.isInfinite(betaUB[i]))
              betaLB[i] *= d;
            if (betaGiven != null) {
              normG += betaGiven[i] * dd;
              betaGiven[i] *= d;
            }
            if (betaStart != null) {
              normS += betaStart[i] * dd;
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
        _bc.setBetaStart(betaStart).setLowerBounds(betaLB).setUpperBounds(betaUB).setProximalPenalty(betaGiven, rho);
      }
      _tInfos = new GLMTaskInfo[_parms._n_folds + 1];
      InitTsk itsk = new InitTsk(0, _dinfo._intercept, null);
      H2O.submitTask(itsk).join();
      assert itsk._ymut != null;
      assert itsk._gtNull != null;
      assert itsk._ymut._nobs == itsk._gtNull._nobs:"unexpected nobs, " + itsk._ymut._nobs + " != " + itsk._gtNull._nobs;// +", filterVec = " + (itsk._gtNull._rowFilter != null) + ", nrows = " + itsk._gtNull._rowFilter.length() + ", mean = " + itsk._gtNull._rowFilter.mean()
      _rowFilter = itsk._ymut._fVec;
      assert _rowFilter.nChunks() == _dinfo._adaptedFrame.anyVec().nChunks();
      assert (_dinfo._adaptedFrame.numRows() - _rowFilter.mean() * _rowFilter.length()) == itsk._ymut._nobs:"unexpected nobs, expected " + itsk._ymut._nobs + ", but got " + _rowFilter.mean() * _rowFilter.length();
      assert _rowFilter != null;
      if (itsk._ymut._nobs == 0) // can happen if all rows have missing value and we're filtering missing out
        error("training_frame", "Got no data to run on after filtering out the rows with missing values.");
      if (itsk._ymut._yMin == itsk._ymut._yMax)
        error("response", "Can not run glm on dataset with constant response. Response == " + itsk._ymut._yMin + " for all rows in the dataset after filtering out rows with NAs, got " + itsk._ymut._nobs + " of rows out of " + _dinfo._adaptedFrame.numRows() + " rows total.");
      if (itsk._ymut._nobs < (_dinfo._adaptedFrame.numRows() >> 1)) { // running less than half of rows?
        warn("training_frame", "Dataset has less than 1/2 of the data after filtering out rows with NAs");
      }
      // GLMTaskInfo(Key dstKey, int foldId, long nobs, double ymu, double lmax, double[] beta, GradientInfo ginfo, double objVal){
      GLMGradientTask gtBetastart = itsk._gtBetaStart != null?itsk._gtBetaStart:itsk._gtNull;
      double lmax =  lmax(itsk._gtNull);
      double l1pen = _parms._alpha[0] * lmax * ArrayUtils.l1norm(_bc._betaStart, _dinfo._intercept);
      _tInfos[0] = new GLMTaskInfo(_dest, 0, itsk._ymut._nobs, itsk._ymut._ymu,lmax,_bc._betaStart, new GradientInfo(gtBetastart._objVal,gtBetastart._gradient),gtBetastart._objVal + l1pen);
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
      m.adaptTestForTrain(_valid,true);
      // _dinfo = new DataInfo(Key.make(), _train, _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
      if(_valid != null)
        _validDinfo = new DataInfo(Key.make(), _valid, null, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, true);
      if(_parms._lambda_search) // todo add xval/hval for null model?
        setSubmodel(_dest,0,_bc._betaStart,gtBetastart._val,null,null);
      if(_parms._max_iterations == -1)
        _parms._max_iterations = _parms._lambda_search?6*_parms._nlambdas:50;
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
      addToPendingCount(1);
      // get filtered dataset's mean and number of observations
      new YMUTask(_dinfo, _dinfo._adaptedFrame.anyVec().makeZero(), new H2OCallback<YMUTask>(this) {
        @Override
        public void callback(final YMUTask ymut) {
          _rowFilter = ymut._fVec;
          _ymut = ymut;
          final double[] beta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
          if(_intercept)
            beta[beta.length-1] = _parms.link(ymut._ymu);
          if (_bc._betaStart == null)
            _bc.setBetaStart(beta);
          // compute the lambda_max
          _gtNull = new GLMGradientTask(_dinfo, _parms, 0, beta, 1.0 / ymut._nobs,_rowFilter,InitTsk.this).asyncExec(_dinfo._adaptedFrame);
          if(beta != _bc._betaStart) {
            InitTsk.this.addToPendingCount(1);
            _gtBetaStart = new GLMGradientTask(_dinfo, _parms, 0, _bc._betaStart, 1.0 / ymut._nobs,_rowFilter,InitTsk.this).asyncExec(_dinfo._adaptedFrame);
          }
        }
      }).asyncExec(_dinfo._adaptedFrame);
    }
  }
  @Override
  public ModelBuilderSchema schema() {
    return new GLMV2();
  }


  @Override
  public Job<GLMModel> trainModel() {
    _parms.read_lock_frames(this);
    start(new GLMDriver(null), _parms._max_iterations);
    return this;
  }

  static double GLM_GRAD_EPS = 1e-4; // done (converged) if subgrad < this value.
  static final int MAX_ITERATIONS_PER_LAMBDA = 10;
  private static final int MAX_ITER = 50;
  static final int sparseCoefThreshold = 750;
  private static final double beta_epsilon = 1e-4;

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
    GradientInfo    _ginfo;      // gradient and penalty of glm + L2 pen.transient double [] _activeBeta;
    double          _objVal;     // full objective value including L1 pen
    int             _iter;
    // these are not strictly state variables
    // I put them here to have all needed info in state object (so I only need to keep State[] info when doing xval)
    final Key             _dstKey;

    public GLMTaskInfo(Key dstKey, int foldId, long nobs, double ymu, double lmax, double[] beta, GradientInfo ginfo, double objVal){
      _dstKey = dstKey;
      _foldId = foldId;
      _nobs = nobs;
      _ymu = ymu;
      _lambdaMax = lmax;
      _beta = beta;
      _ginfo = ginfo;
      _objVal = objVal;
    }


    public void adjustToNewLambda( double currentLambda, double newLambda, double alpha, boolean intercept) {
      assert newLambda < currentLambda:"newLambda = " + newLambda + ", last lambda = " + currentLambda;
      double l2diff = (newLambda - currentLambda) * (1 - alpha);
      if(_activeCols != null) {
        int j = 0;
        for (int i : _activeCols)
          _ginfo._gradient[i] += l2diff * _beta[j++];
      } else {
        for (int i = 0; i < _ginfo._gradient.length - (intercept?1:0); ++i)
          _ginfo._gradient[i] += l2diff * _beta[i];
      }
      _ginfo = new GradientInfo(_ginfo ._objVal + .5 * l2diff * ArrayUtils.l2norm2(_beta, intercept), _ginfo._gradient);
      _objVal = _ginfo._objVal + newLambda * alpha * ArrayUtils.l1norm(_beta,intercept);
    }
  }

  private final double lmax(GLMGradientTask gLmax) {
    return Math.max(ArrayUtils.maxValue(gLmax._gradient),-ArrayUtils.minValue(gLmax._gradient))/Math.max(1e-3,_parms._alpha[0]);
  }

  /**
   * Encapsulates state needed for line search i.e. previous solution and it's gradient and objective value.
   */
  private static final class IterationInfo {
    final double[] _beta;
    final double _likelihood;

    final int _iter;

    public IterationInfo(int iter, double[] beta, double likelihood) {
      _iter = iter;
      _beta = beta;
      _likelihood = likelihood;
    }
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
    }

    @Override public void onCompletion(CountedCompleter cc){
      getCompleter().addToPendingCount(1);
      H2O.submitTask(new FinalizeAndUnlockTsk(new H2OCallback((H2OCountedCompleter) getCompleter()) {
        @Override
        public void callback(H2OCountedCompleter h2OCountedCompleter) {
          doCleanup();
          done();
        }

        @Override
        public boolean onExceptionalCompletion(Throwable ex, CountedCompleter cc) {
          doCleanup();
          new RemoveCall(null, _dest).invokeTask();
          return true;
        }
      }, _dest, _key, _valid != null ? _valid._key : null));
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
        double currentLambda = _parms._lambda[_lambdaId];
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
          double nextLambda = _parms._lambda[_lambdaId];
          getCompleter().addToPendingCount(1);
          if(_parms._n_folds > 1){
            GLMSingleLambdaTsk[] tasks = new GLMSingleLambdaTsk[_tInfos.length];
            H2OCountedCompleter cmp = new LambdaSearchIteration((H2OCountedCompleter)getCompleter());
            cmp.addToPendingCount(tasks.length-1);
            for(int i = 0; i < tasks.length; ++i)
              tasks[i] = new GLMSingleLambdaTsk(cmp,_tInfos[i]);
            new ParallelTasks(new LambdaSearchIteration((H2OCountedCompleter) getCompleter()),tasks).fork();
          } else {
            _tInfos[0].adjustToNewLambda(currentLambda,nextLambda, _parms._alpha[0], _dinfo._intercept);
            new GLMSingleLambdaTsk(new LambdaSearchIteration((H2OCountedCompleter) getCompleter()),  _tInfos[0]).fork();
          }
        }
      }
    }
  }


  private void setSubmodel(Key dstKey, int iter, final double[] fullBeta, GLMValidation trainVal, GLMValidation holdOutVal, H2OCountedCompleter cmp) {
    final double[] newBetaDeNorm;
    if (_dinfo._predictor_transform == DataInfo.TransformType.STANDARDIZE) {
      newBetaDeNorm = fullBeta.clone();
      double norm = 0.0;        // Reverse any normalization on the intercept
      // denormalize only the numeric coefs (categoricals are not normalized)
      final int numoff = _dinfo.numStart();
      for (int i = numoff; i < fullBeta.length - 1; i++) {
        double b = newBetaDeNorm[i] * _dinfo._normMul[i - numoff];
        norm += b * _dinfo._normSub[i - numoff]; // Also accumulate the intercept adjustment
        newBetaDeNorm[i] = b;
      }
      newBetaDeNorm[newBetaDeNorm.length - 1] -= norm;
    } else
      newBetaDeNorm = null;
    GLMModel.setSubmodel(cmp, dstKey, _parms._lambda[_lambdaId], newBetaDeNorm == null ? fullBeta : newBetaDeNorm, newBetaDeNorm == null ? null : fullBeta,iter, System.currentTimeMillis() - _start_time, _dinfo.fullN() >= sparseCoefThreshold, trainVal, holdOutVal);
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
      msg = "GLM2[dest=" + _taskInfo._dstKey + ", iteration=" + _taskInfo._iter + ", lambda = " + _parms._lambda[_lambdaId] + "]: " + msg;
      Log.info(msg);
      return msg;
    }

    double objVal(double likelihood, double[] beta) {
      double alpha = _parms._alpha[0];
      double proximalPen = 0;
      if (_bc._betaGiven != null) {
        for (int i = 0; i < _bc._betaGiven.length; ++i) {
          double diff = beta[i] - _bc._betaGiven[i];
          proximalPen += diff * diff * _bc._rho[i] * .5;
        }
      }
      return likelihood / _taskInfo._nobs
        + proximalPen
        + _parms._lambda[_lambdaId] * (alpha * ArrayUtils.l1norm(beta, _activeData._intercept)
        + (1 - alpha) * .5 * ArrayUtils.l2norm2(beta, _activeData._intercept));
    }


    boolean _allIn;

    /**
     * Apply strong rules to filter out expected innactive (with zero coefficient) predictors.
     *
     * @return indeces of expected active predictors.
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
      double[] fullBeta = (_taskInfo._activeCols == null || newBeta == null) ? newBeta : expandVec(newBeta, _taskInfo._activeCols, _dinfo.fullN() + 1);
      if (fullBeta == null) {
        fullBeta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        fullBeta[fullBeta.length - 1] = _parms.linkInv(_taskInfo._ymu);
      }
      GLM.this.setSubmodel(_taskInfo._dstKey, _taskInfo._iter, fullBeta, trainVal, holdOutVal, cmp);
      return fullBeta;
    }


    /**
     * Computes the full gradient (gradient for all predictors) and checks line search condition (gradient has no NaNs/Infs) and the KKT conditions
     * for the underlying optimization problem. If some inactive columns violate the KKTs,
     * then they are added into the active set and solution is recomputed (rare), otherwise we just update the model in the K/V with this new solution
     * and finish.
     *
     * @param newBeta          - computed solution
     * @param failedLineSearch - boolean flag if we're already comming from failed line-search (unable to proceed) - in that case line search is never performed.
     */
    protected void checkKKTAndComplete(final double[] newBeta, final boolean failedLineSearch) {
      final H2OCountedCompleter cmp = (H2OCountedCompleter) getCompleter();
      cmp.addToPendingCount(1);
      final double[] fullBeta;
      if (newBeta == null) {
        fullBeta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        fullBeta[fullBeta.length - 1] = _parms.linkInv(_taskInfo._ymu);
      } else
        fullBeta = expandVec(newBeta, _taskInfo._activeCols, _dinfo.fullN() + 1);

      // now we need full gradient (on all columns) using this beta
      // GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, H2OCountedCompleter cc){
      new GLMGradientTask(_dinfo, _parms, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), fullBeta, 1.0 / _taskInfo._nobs, _rowFilter,  new H2O.H2OCallback<GLMGradientTask>(cmp) {
        @Override
        public String toString() {
          return "checkKKTAndComplete.Callback, completer = " + getCompleter() == null ? "null" : getCompleter().toString();
        }

        @Override
        public void callback(final GLMGradientTask glrt) {
          // first check KKT conditions!
          final double[] grad = glrt._gradient;
          if (ArrayUtils.hasNaNsOrInfs(grad)) {
            if (!failedLineSearch) {
              LogInfo("Check KKT got NaNs. Invoking line search");
              getCompleter().addToPendingCount(1);
              new GLMLineSearchTask(_activeData, _parms, 1.0 / _taskInfo._nobs, _taskInfo._beta, ArrayUtils.subtract(contractVec(fullBeta, _taskInfo._activeCols), _taskInfo._beta), LINE_SEARCH_STEP, NUM_LINE_SEARCH_STEPS, _rowFilter, new LineSearchIteration(getCompleter())).asyncExec(_activeData._adaptedFrame);
  //              new GLMGradientTask(_activeData, _parms, _currentLambda * (1-_parms._alpha[0]),, NUM_LINE_SEARCH_STEPS, LINE_SEARCH_STEP), 1.0/_taskInfo._nobs, new LineSearchIteration(getCompleter(), ArrayUtils.subtract(newBeta, _lastResult._beta) )).asyncExec(_activeData._adaptedFrame);
              return;
            } else {
              // TODO: add warning and break the lambda search? Or throw Exception?
              LogInfo("got NaNs/Infs in gradient at lambda " + _parms._lambda[_lambdaId]);
            }
          }
          // check the KKT conditions and filter data for the next lambda_value
          // check the gradient
          double[] subgrad = grad.clone();
          ADMM.subgrad(_parms._alpha[0] * _parms._lambda[_lambdaId], fullBeta, subgrad);
          double err = GLM_GRAD_EPS;
          if (!failedLineSearch && _taskInfo._activeCols != null) {
            for (int c : _taskInfo._activeCols)
              if (subgrad[c] > err) err = subgrad[c];
              else if (subgrad[c] < -err) err = -subgrad[c];
            int[] failedCols = new int[64];
            int fcnt = 0;
            for (int i = 0; i < grad.length - 1; ++i) {
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
              Arrays.sort(_taskInfo._activeCols);
              _taskInfo._beta = resizeVec(glrt._beta, newCols, _taskInfo._activeCols, _dinfo.fullN() + (_dinfo._intercept?1:0));
              _taskInfo._activeCols = newCols;
              LogInfo(fcnt + " variables failed KKT conditions check! Adding them to the model and continuing computation.(grad_eps = " + err + ", activeCols = " + (_taskInfo._activeCols.length > 100 ? "lost" : Arrays.toString(_taskInfo._activeCols)));
              _activeData = _dinfo.filterExpandedColumns(_taskInfo._activeCols);
              // NOTE: tricky completer game here:
              // We expect 0 pending in this method since this is the end-point, ( actually it's racy, can be 1 with pending 1 decrement from the original Iteration callback, end result is 0 though)
              // while iteration expects pending count of 1, so we need to increase it here (Iteration itself adds 1 but 1 will be subtracted when we leave this method since we're in the callback which is called by onCompletion!
              // [unlike at the start of nextLambda call when we're not inside onCompletion]))
              getCompleter().addToPendingCount(1);
              new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, true, contractVec(glrt._beta, _taskInfo._activeCols), _taskInfo._ymu,_rowFilter, new Iteration(getCompleter(), false)).asyncExec(_activeData._adaptedFrame);
              return;
            }
          }
          // update the state
          _taskInfo._beta = newBeta;
          _taskInfo._ginfo = new GradientInfo(glrt._objVal, glrt._gradient);
          _taskInfo._objVal = glrt._objVal + (1 - _parms._alpha[0]) * ArrayUtils.l1norm(glrt._beta, _activeData._intercept);
          // todo get validation on the validation set here
          if(_valid != null) {
            cmp.addToPendingCount(2);
            assert cmp.getPendingCount() > 0;
            // public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, H2OCountedCompleter cc){
            new GLMTask.GLMGradientTask(_dinfo, _parms, _parms._lambda[_lambdaId], glrt._beta, 1.0 / _taskInfo._nobs, null /* no rowf filter for validation dataset */, new H2OCallback<GLMGradientTask>(cmp) {
              @Override
              public void callback(GLMGradientTask gt) {
                LogInfo("hold-out set validation: \n" + gt._val.toString());
                setSubmodel(newBeta, glrt._val, gt._val, cmp);
                cmp.tryComplete();
              }
            }).setValidate(_taskInfo._ymu,true).asyncExec(_validDinfo._adaptedFrame);
            // public GLMGradientTask(DataInfo dinfo, GLMParameters params, double lambda, double[] beta, double reg, H2OCountedCompleter cc){
//            new GLMTask.GLMGradientTask(_dinfo,_parms,_parms._lambda[_lambdaId],)
          } else
            setSubmodel(newBeta, glrt._val, null, cmp);
          System.out.println("haha, cmp.pending = " + cmp.getPendingCount());
        }
      }).setValidate(_taskInfo._ymu, true).asyncExec(_dinfo._adaptedFrame);

    }

    private final boolean isSparse(Frame f) {
      int scount = 0;
      for (Vec v : f.vecs())
        if ((v.nzCnt() << 3) > v.length())
          scount++;
      return (f.numCols() >> 1) < scount;
    }

    private GradientInfo adjustL2(GradientInfo ginfo, double[] coefs, double lambdaDiff) {
      for (int i = 0; i < coefs.length - 1; ++i)
        ginfo._gradient[i] += lambdaDiff * coefs[i];
      return ginfo;
    }

    @Override
    protected void compute2() {
      assert _rowFilter != null;
      _start_time = System.currentTimeMillis();
      LogInfo("lambda = " + _parms._lambda[_lambdaId] + "\n");
      int[] activeCols = activeCols(_parms._lambda[_lambdaId], _lambdaId == 0?_taskInfo._lambdaMax:_parms._lambda[_lambdaId-1], _taskInfo._ginfo._gradient);
      _taskInfo._beta = resizeVec(_taskInfo._beta, activeCols, _taskInfo._activeCols , _dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
      _taskInfo._activeCols = activeCols;
      _activeData = _dinfo.filterExpandedColumns(activeCols);
      assert  activeCols == null || _activeData.fullN() == activeCols.length : LogInfo("mismatched number of cols, got " + activeCols.length + " active cols, but data info claims " + _activeData.fullN());
      assert DKV.get(_activeData._key) != null;
      int n = activeCols == null ? _dinfo.fullN() : activeCols.length;
      if (n > _parms._max_active_predictors)
        throw new TooManyPredictorsException();
      boolean LBFGS = _parms._solver == Solver.L_BFGS;

      if (LBFGS) { // TODO add L1 pen handling!
        double[] beta = _taskInfo._beta;
        if (beta == null) {
          beta = MemoryManager.malloc8d(_activeData.fullN() + (_activeData._intercept ? 1 : 0));
          if (_activeData._intercept)
            beta[beta.length - 1] = _parms.link(_taskInfo._ymu);
        }
        if (_parms._alpha[0] > 0 || _taskInfo._activeCols != null)
          throw H2O.unimpl();
        GradientSolver solver = new GLMGradientSolver(_parms, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _taskInfo._ymu, _taskInfo._nobs, _bc._betaGiven, _bc._rho, 0, _rowFilter);
        final long t1 = System.currentTimeMillis();
        L_BFGS lbfgs = new L_BFGS().setMaxIter(_parms._max_iterations);
        Result r = lbfgs.solve(solver, beta, _taskInfo._ginfo, new ProgressMonitor() {
          @Override
          public boolean progress(double[] beta, GradientInfo ginfo) {
            if ((_taskInfo._iter & 7) == 0) {
              update(8, "iteration " + (_taskInfo._iter + 1) + ", objective value = " + ginfo._objVal, GLM.this._key);
              LogInfo("LBFGS: objval = " + ginfo._objVal);
            }
            ++_taskInfo._iter;
            // todo update the model here so we can show intermediate results
            return isRunning(GLM.this._key);
          }
        });
        long t2 = System.currentTimeMillis();
        Log.info("L_BFGS (k = " + lbfgs.k() + ") done after " + r.iter + " iterations and " + ((t2 - t1) / 1000) + " seconds, objval = " + r.ginfo._objVal + ", penalty = " + (_parms._lambda[_lambdaId] * .5 * ArrayUtils.l2norm2(beta, true)) + ",  gradient norm2 = " + (MathUtils.l2norm2(r.ginfo._gradient)));
        _taskInfo._ginfo = r.ginfo;
        double[] newBeta = r.coefs;
        // update the state
        _taskInfo._beta = newBeta;
        _taskInfo._iter += r.iter;
        setSubmodel(newBeta, null, null, this);
        tryComplete();
      } else // fork off ADMM iteration
        new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, false, _taskInfo._beta, _taskInfo._ymu, _rowFilter, new Iteration(this, false)).asyncExec(_activeData._adaptedFrame);
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
        if (!isRunning(GLM.this._key)) throw new JobCancelledException();
        assert glmt._nobs == _taskInfo._nobs:"got wrong number of observations, expected " + _taskInfo._nobs + ", but got " + glmt._nobs + ", got row filter?" + (glmt._rowFilter != null);
        assert _taskInfo._activeCols == null || glmt._beta == null || glmt._beta.length == (_taskInfo._activeCols.length + 1) : LogInfo("betalen = " + glmt._beta.length + ", activecols = " + _taskInfo._activeCols.length);
        assert _taskInfo._activeCols == null || _taskInfo._activeCols.length == _activeData.fullN();
        double reg = 1.0 / _taskInfo._nobs;
        glmt._gram.mul(reg);
        ArrayUtils.mult(glmt._xy, reg);
        if (_countIteration) ++_taskInfo._iter;
        long callbackStart = System.currentTimeMillis();
        double objVal = objVal(glmt._likelihood, glmt._beta);
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
          System.out.println("setting new objVal to " + objVal + ", from " + lastObjVal);
          _taskInfo._ginfo = null;
        }

        final double[] newBeta = MemoryManager.malloc8d(glmt._xy.length);
        double l2pen = _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]);
        double l1pen = _parms._lambda[_lambdaId] * _parms._alpha[0];
        double defaultRho = _bc._betaLB != null || _bc._betaUB != null ? _taskInfo._lambdaMax * 1e-2 : 0;
        // l1pen or upper/lower bounds require ADMM solver
        if (l1pen > 0 || _bc._betaLB != null || _bc._betaUB != null) {
          // double rho = Math.max(1e-4*_taskInfo._lambdaMax*_parms._alpha[0],_currentLambda*_parms._alpha[0]);
          long tx = System.currentTimeMillis();
          GramSolver gslvr = new GramSolver(glmt._gram, glmt._xy, _activeData._intercept, l2pen, l1pen /*, rho*/, _bc._betaGiven, _bc._rho, defaultRho, _bc._betaLB, _bc._betaUB);
          long ty = System.currentTimeMillis();
          new ADMM.L1Solver(1e-4, 5000).solve(gslvr, newBeta, l1pen, _activeData._intercept, _bc._betaLB, _bc._betaUB);
          LogInfo("ADMM done in " + (System.currentTimeMillis() - tx) + "ms, cholesky took " + (tx - ty) + "ms");
        } else
          new GramSolver(glmt._gram, glmt._xy, _activeData._intercept, l2pen /*, 0*/, l1pen, _bc._betaGiven, _bc._rho, defaultRho, _bc._betaLB, _bc._betaUB).solve(null, newBeta);
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
            double[] beta = _parms._family == Family.gaussian ? newBeta : glmt._beta;
            checkKKTAndComplete(beta, false);
            return;
          } else { // not done yet, launch next iteration
            if (glmt._beta != null)
              setSubmodel(glmt._beta, glmt._val, null,  (H2OCountedCompleter) getCompleter().getCompleter()); // update current intermediate result
            final boolean validate = (_taskInfo._iter % 5) == 0;
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), glmt._glm, validate, newBeta, _taskInfo._ymu, _rowFilter,new Iteration(getCompleter(), true)).asyncExec(_activeData._adaptedFrame);
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
          if (_taskInfo._objVal > objVal(lst._likelihoods[i], beta)) {
            assert t < 1;
            LogInfo("line search: found admissible step = " + t + ",  objval = " + lst._likelihoods[i]);
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, true, beta, _taskInfo._ymu, _rowFilter, new Iteration(getCompleter(), true, false)).asyncExec(_activeData._adaptedFrame);
            return;
          }
        }
        // no line step worked => converge
        LogInfo("Line search did not find feasible step, converged at objval = " + _taskInfo._objVal);
        checkKKTAndComplete(lst._beta, true);
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

    private final double[] expandVec(double[] beta, final int[] activeCols, int fullN) {
      assert beta != null;
      if (activeCols == null) return beta;
      double[] res = MemoryManager.malloc8d(fullN);
      int i = 0;
      for (int c : activeCols)
        res[c] = beta[i++];
      res[res.length - 1] = beta[beta.length - 1];
      return res;
    }

    private final double[] contractVec(double[] beta, final int[] activeCols) {
      if (beta == null) return null;
      if (activeCols == null) return beta.clone();
      double[] res = MemoryManager.malloc8d(activeCols.length + 1);
      int i = 0;
      for (int c : activeCols)
        res[i++] = beta[c];
      res[res.length - 1] = beta[beta.length - 1];
      return res;
    }
    private final double[] resizeVec(double[] beta, final int[] activeCols, final int[] oldActiveCols, int fullN) {
      if (beta == null || Arrays.equals(activeCols, oldActiveCols)) return beta;
      double[] full = expandVec(beta, oldActiveCols, fullN);
      if (activeCols == null) return full;
      return contractVec(full, activeCols);
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

    double _addedL2;
    double  [] _rho;

    private static double boundedX(double x, double lb, double ub) {
      if(x < lb)x = lb;
      if(x > ub)x = ub;
      return x;
    }

    public GramSolver(Gram gram, double[] xy, boolean intercept, double l2pen, double l1pen, double[] beta_given, double[] proxPen, double default_rho, double[] lb, double[] ub) {
      if(ub != null && lb != null)
        for(int i = 0; i < ub.length; ++i) {
          assert ub[i] >= lb[i]:i + ": ub < lb, ub = " + Arrays.toString(ub) + ", lb = " + Arrays.toString(lb) ;
        }
      _lambda = l2pen;
      _gram = gram;
      int ii = intercept?1:0;
      int icptCol = xy.length-1;
      double [] rhos = MemoryManager.malloc8d(xy.length);
      double min = Double.POSITIVE_INFINITY;
      for (int i = 0; i < xy.length - ii; ++i) {
        double d = xy[i];
        d = d >= 0 ? d : -d;
        if (d < min && d != 0) min = d;
      }
      double ybar = xy[icptCol];
      for (int i = 0; i < rhos.length - ii; ++i) {
        double y = xy[i];
        if (y == 0) y = min;
        double xbar = gram.get(icptCol,i);
        double x = (beta_given != null && proxPen != null)
          ?(y - ybar * gram.get(icptCol,i) + proxPen[i] * beta_given[i]) / ((gram.get(i, i) - xbar * xbar) + l2pen + proxPen[i])
          :((y - ybar * xbar)/ (gram.get(i, i) - xbar * xbar) + l2pen);///gram.get(i,i);
        double rho = 1e-6;
        if(x != 0) {
          rho = Math.abs(l1pen / x);
          double D = l1pen*(l1pen + 4*x);
          if(D >= 0) {
            D = Math.sqrt(D);
            double r = .25 * (l1pen + D) / (2 * x);
            if(r > 0) rho = r;
          }
        } else if(x < 0) {
          double D = l1pen * (l1pen - 4 * x);
          if(D >= 0) {
            D = Math.sqrt(D);
            double r = -.25 * (l1pen + D) / (2 * x);
            if(r > 0) rho = r;
          }
        }
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
      if(lb != null && !Double.isInfinite(lb[icptCol])|| ub != null && !Double.isInfinite(ub[icptCol])) {
        int icpt = xy.length-1;
        rhos[icpt] = 1;//(xy[icpt] >= 0 ? xy[icpt] : -xy[icpt]);
      }
      if(l2pen > 0)
        gram.addDiag(l2pen);
      if(proxPen != null && beta_given != null) {
        gram.addDiag(proxPen);
        xy = xy.clone();
        for(int i = 0; i < xy.length; ++i)
          xy[i] += proxPen[i]*beta_given[i];
      }
      gram.addDiag(rhos);
      _chol = gram.cholesky(null,true,null);
      double l2 = 1e-8;
      while(!_chol.isSPD() && _addedL2 < 1) { // need to add l2
        _gram.addDiag(l2 - _addedL2);
        _addedL2 = l2;
        l2 *= 10;
        _chol = _gram.cholesky(_chol);
      }
      gram.addDiag(ArrayUtils.mult(rhos, -1));
      ArrayUtils.mult(rhos, -1);
      _rho = rhos;
      _xy = xy;
    }

    public double [] nullGradient(double ybar){
      double [] beta = MemoryManager.malloc8d(_xy.length);
      beta[beta.length-1] = ybar;
      return ArrayUtils.subtract(_gram.mul(beta), _xy) ;
    }

    public double addedL2(){return _addedL2;}
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
  }

  /**
   * Gradient and line search computation for L_BFGS and also L_BFGS solver wrapper (for ADMM)
   */
  public static final class GLMGradientSolver extends GradientSolver implements ProximalSolver {
    final GLMParameters _glmp;
    final DataInfo _dinfo;
    final double _ymu;
    final double _lambda;
    final long _nobs;
    int _nsteps = 32;
    double[] _betaGiven;
    final double[] _proximalPen;
    double _rho;
    Vec _rowFilter;

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, long nobs) {
      this(glmp, dinfo, lambda, ymu, nobs, null, null,0, null);
    }

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, long nobs, double[] betaGiven, double[] proximalPen, double rho, Vec rowFilter) {
      _glmp = glmp;
      _dinfo = dinfo;
      _ymu = ymu;
      _nobs = nobs;
      _lambda = lambda;
      _stepDec = LINE_SEARCH_STEP;
      _betaGiven = betaGiven;
      _proximalPen = proximalPen;
      _rho = rho;
      _rowFilter = rowFilter;
    }

    public GLMGradientSolver setBetaStart(double [] beta) {
      _beta = beta.clone();
      return this;
    }

    @Override
    public GradientInfo getGradient(double[] beta) {
      GLMGradientTask gt = _glmp._family == Family.binomial
        ? new LBFGS_LogisticGradientTask(_dinfo, _glmp, _lambda, beta, 1.0 / _nobs, _rowFilter ).doAll(_dinfo._adaptedFrame)
        : new GLMGradientTask(_dinfo, _glmp, _lambda, beta, 1.0 / _nobs, _rowFilter).doAll(_dinfo._adaptedFrame);
      if (_betaGiven != null) { // add proximal gradient
        for (int i = 0; i < gt._gradient.length; ++i) {
          double diff = (beta[i] - _betaGiven[i]);
          double pen = _proximalPen[i] * diff;
          gt._gradient[i] += pen;
          gt._objVal += .5*pen*diff;
        }
      }
      return new GradientInfo(gt._objVal, gt._gradient);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] direction) {
      double reg = 1.0 / _nobs;
      double[] objs = new GLMLineSearchTask(_dinfo, _glmp, 1.0 / _nobs, beta, direction, _stepDec, _nsteps, _rowFilter).doAll(_dinfo._adaptedFrame)._likelihoods;
      double step = 1;
      for (int i = 0; i < objs.length; ++i, step *= _stepDec) {
        objs[i] *= reg;
        if (_lambda > 0 || _betaGiven != null) { // have some l2 pen
          double[] b = ArrayUtils.wadd(beta.clone(), direction, step);
          if (_lambda > 0)
            objs[i] += .5 * _lambda * ArrayUtils.l2norm2(b, _dinfo._intercept);
          if (_betaGiven != null && _proximalPen != null) {
            for (int j = 0; j < _betaGiven.length; ++j) {
              double diff = b[j] - _betaGiven[j];
              objs[i] += .5 * _proximalPen[j] * diff * diff;
            }
          }
        }
      }
      return objs;
    }

    @Override
    public double []  rho() {
      return new double []{_rho};
    }

    double [] _beta;
    double [] _gradient;
    @Override
    public void solve(double[] beta_given, double[] result) {
      if(_beta == null)
        throw new IllegalArgumentException("Can not call solve without beta_start, call setBetaStart() first");
      if(_betaGiven == null)
        _betaGiven = beta_given.clone();
      else
        System.arraycopy(beta_given, 0, _betaGiven, 0, beta_given.length);
      Result r = new L_BFGS().solve(this, _beta);
      System.arraycopy(r.coefs, 0, _beta, 0, r.coefs.length);
      if (_gradient == null)
        _gradient = r.ginfo._gradient.clone();
      else
        System.arraycopy(r.ginfo._gradient, 0, _gradient, 0, _gradient.length);
    }
    @Override
    public boolean hasGradient() {
      return _gradient != null;
    }

    @Override
    public double[] gradient(double[] beta) { return _gradient; }

    @Override
    public void setRho(double [] rho) { _rho = rho[0];}

    @Override
    public boolean canSetRho() { return true;}
  }

}
