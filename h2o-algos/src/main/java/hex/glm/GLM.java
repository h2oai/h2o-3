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
      assert itsk._ymut._nobs == itsk._gtNull._nobs:"unexpected nobs, " + itsk._ymut._nobs + " != " + itsk._gtNull._nobs +", filterVec = " + (itsk._gtNull._rowFilter != null) + ", nrows = " + itsk._gtNull._rowFilter.length() + ", mean = " + itsk._gtNull._rowFilter.mean();
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
      _tInfos[0] = new GLMTaskInfo(_dest, 0, itsk._ymut._nobs, itsk._ymut._ymu,lmax,_bc._betaStart, _dinfo.fullN() + (_dinfo._intercept?1:0),new GradientInfo(gtBetastart._objVal,gtBetastart._gradient),gtBetastart._objVal + l1pen);
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
      if(_parms._max_iter == -1)
        _parms._max_iter = _parms._lambda_search?6*_parms._nlambdas:50;
      if(_parms._solver == Solver.COORDINATE_DESCENT) { // make needed vecs
        double eta = _parms.link(_tInfos[0]._ymu);
        _tInfos[0]._eVec = _dinfo._adaptedFrame.anyVec().makeCon(eta);
        _tInfos[0]._wVec = _dinfo._adaptedFrame.anyVec().makeCon(1);
        _tInfos[0]._zVec = _dinfo._adaptedFrame.lastVec().makeCopy(null);
        _tInfos[0]._iVec = _dinfo._adaptedFrame.anyVec().makeCon(1);
      }
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
      new YMUTask(_dinfo, _dinfo._adaptedFrame.anyVec().makeZero(), new H2OCallback<YMUTask>() {
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
    start(new GLMDriver(null), _parms._max_iter);
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

    public boolean hasBounds(){
      if(_betaLB != null)
        for(double d:_betaLB)
          if(!Double.isInfinite(d)) return true;
      if(_betaUB != null)
        for(double d:_betaUB)
          if(!Double.isInfinite(d)) return true;
      return false;
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

    // vecs used by cooridnate descent
    Vec _eVec; // eta
    Vec _wVec; // weights
    Vec _zVec; // z
    Vec _iVec; // intercept - all 1s
    final int _fullN;

    public GLMTaskInfo(Key dstKey, int foldId, long nobs, double ymu, double lmax, double[] beta, int fullN, GradientInfo ginfo, double objVal){
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
      double l2diff = (newLambda - currentLambda) * (1 - alpha);
      Log.info("beta size = " + _beta.length + ", grad size = " + _ginfo._gradient.length);
      for (int i = 0; i < _ginfo._gradient.length - (intercept?1:0); ++i)
        _ginfo._gradient[i] += l2diff * _beta[i];
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
      if(_tInfos[0]._wVec != null)
        _tInfos[0]._wVec.remove();
      if(_tInfos[0]._zVec != null)
        _tInfos[0]._zVec.remove();
      if(_tInfos[0]._eVec != null)
        _tInfos[0]._eVec.remove();
      if(_tInfos[0]._iVec != null)
        _tInfos[0]._iVec.remove();
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
        assert _tInfos[0]._ginfo._gradient.length == _dinfo.fullN()+(_dinfo._intercept?1:0);
        Log.info("Gradient err at lambda = " + _parms._lambda[_lambdaId] + " = " + _tInfos[0].gradientCheck(_parms._lambda[_lambdaId], _parms._alpha[0]));
        int rank = 0;
        for(int i = 0; i < _tInfos[0]._beta.length - (_dinfo._intercept?1:0); ++i)
          if(_tInfos[0]._beta[i] != 0) ++rank;
        Log.info("Solution at lambda = " + _parms._lambda[_lambdaId] + "has " + rank + " nonzeros");
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
        if(++_lambdaId  < _parms._lambda.length && _tInfos[0]._iter < _parms._max_iter) {
          getCompleter().addToPendingCount(1);
          if(_parms._n_folds > 1){
            GLMSingleLambdaTsk[] tasks = new GLMSingleLambdaTsk[_tInfos.length];
            H2OCountedCompleter cmp = new LambdaSearchIteration((H2OCountedCompleter)getCompleter());
            cmp.addToPendingCount(tasks.length-1);
            for(int i = 0; i < tasks.length; ++i)
              tasks[i] = new GLMSingleLambdaTsk(cmp,_tInfos[i]);
            new ParallelTasks(new LambdaSearchIteration((H2OCountedCompleter)getCompleter()),tasks).fork();
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

    protected void solve(){
      if (_activeData.fullN() > _parms._max_active_predictors)
        throw new TooManyPredictorsException();
      switch(_parms._solver) {
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
          L_BFGS lbfgs = new L_BFGS().setMaxIter(_parms._max_iter);
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
            double avg = 0;
            for(int i = 0; i < rho.length - (_dinfo._intercept?1:0); ++i)
              avg += rho[i] = ADMM.L1Solver.estimateRho(-g[i], l1pen);
//            avg /= rho.length - (_dinfo._intercept?1:0);
//            for(int i = 0; i < rho.length - (_dinfo._intercept?1:0); ++i)
//              rho[i] = Math.max(Math.min(rho[i],1.5*avg),0.5*avg);
            new ADMM.L1Solver(1e-4, 1000).solve(new LBFGS_ProximalSolver(solver,_taskInfo._beta,rho, GLM.this._key), _taskInfo._beta, l1pen);
          } else {
            Result r = lbfgs.solve(solver, beta, _taskInfo._ginfo, new ProgressMonitor() {
              @Override
              public boolean progress(double[] beta, GradientInfo ginfo) {
                if ((_taskInfo._iter & 15) == 0) {
                  update(16, "iteration " + (_taskInfo._iter + 1) + ", objective value = " + ginfo._objVal, GLM.this._key);
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
        case ADMM:// fork off ADMM iteration
          new GLMIterationTask(GLM.this._key, _activeData, _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]), _parms, false, _taskInfo._beta, _taskInfo._ymu, _rowFilter, new Iteration(this, false)).asyncExec(_activeData._adaptedFrame);
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
              Arrays.sort(_taskInfo._activeCols);
              _taskInfo._beta = resizeVec(gt1._beta, newCols, _taskInfo._activeCols, _dinfo.fullN() + 1);
              _taskInfo._activeCols = newCols;
              LogInfo(fcnt + " variables failed KKT conditions check! Adding them to the model and continuing computation.(grad_eps = " + err + ", activeCols = " + (_taskInfo._activeCols.length > 100 ? "lost" : Arrays.toString(_taskInfo._activeCols)));
              _activeData = _dinfo.filterExpandedColumns(_taskInfo._activeCols);
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
            }).setValidate(_taskInfo._ymu, true).asyncExec(_validDinfo._adaptedFrame);
          } else {
            setSubmodel(_taskInfo._beta, gt1._val, null, null);
          }
          // got valida solution, update the state and complete
          _taskInfo._ginfo = new GradientInfo(gt1._objVal, gt1._gradient);
          assert _taskInfo._ginfo._gradient.length == _dinfo.fullN() + (_dinfo._intercept?1:0);
          _taskInfo._objVal = gt1._objVal + (1 - _parms._alpha[0]) * ArrayUtils.l1norm(_taskInfo._beta, _activeData._intercept);
          _taskInfo._beta = fullBeta;
        }
      }).setValidate(_taskInfo._ymu,true).asyncExec(_dinfo._adaptedFrame);
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
      _taskInfo._ginfo = new GradientInfo(_taskInfo._ginfo._objVal,contractVec(_taskInfo._ginfo._gradient,activeCols));
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
          new ADMM.L1Solver(1e-4, 500).solve(gslvr, newBeta, l1pen, _activeData._intercept, _bc._betaLB, _bc._betaUB);
          LogInfo("ADMM done in " + (System.currentTimeMillis() - tx) + "ms, cholesky took " + (tx - ty) + "ms");
        } else
          new GramSolver(glmt._gram, glmt._xy, _activeData._intercept, l2pen /*, 0*/, l1pen, _bc._betaGiven, _bc._rho, defaultRho, _bc._betaLB, _bc._betaUB).solve(null, newBeta);
        if (ArrayUtils.hasNaNsOrInfs(newBeta)) {
          throw new RuntimeException(LogInfo("got NaNs and/or Infs in beta"));
        } else {
          final double bdiff = beta_diff(glmt._beta, newBeta);
          if ((_parms._family == Family.gaussian && _parms._link == Link.identity) || bdiff < _parms._beta_epsilon || _taskInfo._iter >= _parms._max_iter) { // Gaussian is non-iterative and gradient is ADMMSolver's gradient => just validate and move on to the next lambda_value
            int diff = (int) Math.log10(bdiff);
            int nzs = 0;
            for (int i = 0; i < newBeta.length; ++i)
              if (newBeta[i] != 0) ++nzs;
            LogInfo("converged (reached a fixed point with ~ 1e" + diff + " precision), got " + nzs + " nzs");
            _taskInfo._beta = _parms._family == Family.gaussian ? newBeta : glmt._beta;
            checkKKTsAndComplete();
            return;
          } else { // not done yet, launch next iteration
            if (glmt._beta != null)
              setSubmodel(glmt._beta, glmt._val, null,  (H2OCountedCompleter) getCompleter().getCompleter()); // update current intermediate result
            final boolean validate = (_taskInfo._iter % 5) == 0;
            if(validate) { // compute validation and hold-out validation and gradient

            }
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

      L_BFGS.Result r  = new L_BFGS().solve(s,result.clone(),_ginfo,new ProgressMonitor(){
          public boolean progress(double [] beta, GradientInfo ginfo){return _jobKey == null || Job.isRunning(_jobKey);}
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
    public double[] getObjVals(double[] beta, double[] pk) {
      double [] objs = _solver.getObjVals(beta,pk);
      double step = 1;
      for (int i = 0; i < objs.length; ++i, step *= _solver.stepDec()) {
        double[] b = ArrayUtils.wadd(beta.clone(), pk, step);
        double pen = 0;
        for (int j = 0; j < _betaGiven.length; ++j) {
          double diff = b[j] - _betaGiven[j];
          pen += .5 * _rho[j] * diff * diff;
        }
        objs[i] += pen;
      }
      return objs;
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
    int _nsteps = 32;
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
      _stepDec = LINE_SEARCH_STEP;
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
      return new GradientInfo(gt._objVal, gt._gradient);
    }

    @Override
    public double[] getObjVals(double[] beta, double[] direction) {
      double reg = 1.0 / _nobs;
      double[] objs = new GLMLineSearchTask(_dinfo, _glmp, 1.0 / _nobs, beta, direction, _stepDec, _nsteps, _rowFilter).doAll(_dinfo._adaptedFrame)._likelihoods;
      double step = 1;
      for (int i = 0; i < objs.length; ++i, step *= _stepDec) {
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
