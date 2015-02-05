package hex.glm;

import hex.FrameTask.DataInfo;
import hex.FrameTask;
import hex.Model;
import hex.SupervisedModelBuilder;
import hex.glm.GLMModel.FinalizeAndUnlockTsk;
import hex.glm.GLMModel.GLMOutput;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMTask.*;
import hex.glm.LSMSolver.ADMMSolver;
import hex.optimization.L_BFGS;
import hex.optimization.L_BFGS.*;
import hex.schemas.GLMV2;
import hex.schemas.ModelBuilderSchema;
import jsr166y.CountedCompleter;
import water.*;
import water.H2O.H2OCallback;
import water.H2O.H2OCountedCompleter;
import water.util.ArrayUtils;
import water.util.Log;
import water.util.MRUtils.ParallelTasks;
import water.util.MathUtils;
import water.util.ModelUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 * TODO: GLM will use a threshold during predict to do binomial classification, but
 * GLMModel currently never returns Binomial as its ModelCategory.
 */
public class GLM extends SupervisedModelBuilder<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> {
  @Override
  public Model.ModelCategory[] can_build() {
    return new Model.ModelCategory[]{
            Model.ModelCategory.Regression,
            // Model.ModelCategory.Binomial, // see TODO comment above.
    };
  }

  public GLM(Key dest, String desc, GLMModel.GLMParameters parms) { super(dest, desc, parms); init(false); }
  public GLM(GLMModel.GLMParameters parms) { super("GLM", parms); init(false); }

  private static class TooManyPredictorsException extends RuntimeException {}

  @Override public void init(boolean expensive) {
    super.init(expensive);
    if(expensive) {
      if(_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
    }
    _parms.validate(this);

  }

  @Override
  public ModelBuilderSchema schema() {
    return new GLMV2();
  }

  private static final int WORK_TOTAL = 100000000;
  private boolean _clean_enums;
  @Override
  public Job<GLMModel> trainModel() {
    _clean_enums = _parms._convert_to_enum && !_response.isEnum();
    _parms.read_lock_frames(this);
    init(true);                 // Expensive tests & conversions
    DataInfo dinfo = new DataInfo(Key.make(),_train,_valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE);
    DKV.put(dinfo._key,dinfo);
    H2OCountedCompleter cmp = new H2OCountedCompleter(){
      AtomicBoolean _gotException = new AtomicBoolean(false);
      @Override public void compute2(){}
      @Override
      public void onCompletion(CountedCompleter cc){
        done();
        _parms.read_unlock_frames(GLM.this);
        if( _clean_enums ) {
          train().lastVec().remove();
          if (valid() != null) valid().lastVec().remove();
        }
      }
      @Override public boolean onExceptionalCompletion(Throwable ex, CountedCompleter cc){
        if(!_gotException.getAndSet(true)) {
          failed(ex);
          _parms.read_unlock_frames(GLM.this);
          if( _clean_enums ) {
            train().lastVec().remove();
            if (valid() != null) valid().lastVec().remove();
          }
          return true;
        }
        return false;
      }
    };
    start(cmp, WORK_TOTAL);
    H2O.submitTask(new GLMDriver(cmp, dinfo));
    return this;
  }

  private static double GLM_GRAD_EPS = 1e-4; // done (converged) if subgrad < this value.
  private static final int MAX_ITERATIONS_PER_LAMBDA = 10;
  private static final int MAX_ITER = 50;
  private static final int sparseCoefThreshold = 750;
  private static final double beta_epsilon = 1e-4;

  /**
   * Encapsulates state of the computation.
   */
  public static final class GLMTaskInfo extends Iced {
    final long      _nobs;     // number of observations in our dataset
    final double    _ymu;      // actual mean of the response
    final double    _lambdaMax;// lambda max of the current dataset
    double [] _beta;     // full solution at previous lambda (or null)
    double [] _gradient; // full gradient at previous beta (or null)
    int             _iter;
    int             _max_iter;
    double _lastLambda;
    float        [] _thresholds;
    double          _objval;
    // these are not strictly state variables
    // I put them here to have all needed info in state object (so I only need to keep State[] info when doing xval)
    final Key             _dstKey;
    final DataInfo        _dinfo;
    final GLMParameters   _params;

    L_BFGS _lbfgs;      // lbfgs solver if we use lbfgs or null
    GradientInfo _gOld; // gradient info of the last returned result

    public GLMTaskInfo(Key dstKey, DataInfo dinfo, GLMParameters params, long nobs, double ymu, double lmax, double lambda, double[] beta, double[] gradient, double objval){
      _dstKey = dstKey;
      _dinfo = dinfo;
      _params = params;
      _nobs = nobs;
      _ymu = ymu;
      _lambdaMax = lmax;
      _lastLambda = lambda;
      _beta = beta;
      _gradient = gradient;
      _max_iter = _params._lambda_search ?MAX_ITERATIONS_PER_LAMBDA:MAX_ITER;
      _objval = objval;
      if(_params._family == Family.binomial)
        _thresholds = ModelUtils.DEFAULT_THRESHOLDS;

    }
  }


  /**
   * Task to compute GLM solution for a particular (single) lambda value.
   * Can be warmstarted by passing in a state of previous computation so e.g. incremental strong rules can be
   * applied.
   *
   * The performs iterative reweighted least squares algorithm with elastic net penalty.
   *
   */
  public static final class GLMLambdaTask extends DTask<GLMLambdaTask>{
    FrameTask.DataInfo _activeData;
    GLMTaskInfo _taskInfo;
    final double _currentLambda;
    int _iter;
    final Key _jobKey;
    Key _progressKey;
    long _start_time;
    double _addedL2;
    final boolean _forceLBFGS;
    public GLMLambdaTask(H2OCountedCompleter cmp, Key jobKey, Key progressKey, GLMTaskInfo state, double lambda, boolean forceLBFGS){
      super(cmp);
      _taskInfo = state;
      assert DKV.get(_taskInfo._dinfo._key) != null;
      _currentLambda = lambda;
      _jobKey = jobKey;
      _progressKey = progressKey;
      _forceLBFGS = forceLBFGS;
    }

    private String LogInfo(String msg){
      msg = "GLM2[dest=" + _taskInfo._dstKey + ", iteration=" + _iter + ", lambda = " + _currentLambda + "]: " + msg;
      Log.info(msg);
      return msg;
    }
    int [] _activeCols;

    /**
     * Apply strong rules to filter out expected innactive (with zero coefficient) predictors.
     * @return indeces of expected active predictors.
     */
    private int [] activeCols(final double l1, final double l2, final double [] grad) {
      int selected = 0;
      int [] cols = null;
      if (_taskInfo._params._alpha[0] > 0) {
        final double rhs = _taskInfo._params._alpha[0] * (2 * l1 - l2);
        cols = MemoryManager.malloc4(_taskInfo._dinfo.fullN());
        int j = 0;
        if (_activeCols == null) _activeCols = new int[]{-1};
        for (int i = 0; i < _taskInfo._dinfo.fullN(); ++i)
          if ((j < _activeCols.length && i == _activeCols[j]) || grad[i] > rhs || grad[i] < -rhs) {
            cols[selected++] = i;
            if (j < _activeCols.length && i == _activeCols[j]) ++j;
          }
      }
      if(_taskInfo._params._alpha[0] == 0 || selected == _taskInfo._dinfo.fullN()){
        _activeCols = null;
        _activeData = _taskInfo._dinfo;
        selected = _taskInfo._dinfo.fullN();
      } else {
        _activeCols = Arrays.copyOf(cols, selected);
        _activeData = _taskInfo._dinfo.filterExpandedColumns(_activeCols);
        assert DKV.get(_activeData._key) != null;
      }
      LogInfo("strong rule at lambda_value=" + l1 + ", got " + selected + " active cols out of " + _taskInfo._dinfo.fullN() + " total.");
      assert _activeCols == null || _activeData.fullN() == _activeCols.length:LogInfo("mismatched number of cols, got " + _activeCols.length + " active cols, but data info claims " + _activeData.fullN());
      return _activeCols;
    }

    /**
     * Encapsulates state needed for line search i.e. previous solution and it's gradient and objective value.
     */
    private static final class IterationInfo {
      final double [] _beta;
      final double [] _grad;
      final double    _objval;
      final int       _iter;
      public IterationInfo(int iter, double [] beta, double [] grad, double objval){
        _iter = iter;
        _beta = beta;
        _grad = grad;
        _objval = objval;
      }
    }
    private transient IterationInfo _lastResult;

    private double [] setSubmodel(final double[] newBeta, GLMValidation val, H2O.H2OCountedCompleter cmp){
      double [] fullBeta = (_activeCols == null || newBeta == null)?newBeta:expandVec(newBeta,_activeCols, _taskInfo._dinfo.fullN()+1);
      if(fullBeta == null){
        fullBeta = MemoryManager.malloc8d(_taskInfo._dinfo.fullN()+1);
        fullBeta[fullBeta.length-1] = _taskInfo._params.linkInv(_taskInfo._ymu);
      }
      final double [] newBetaDeNorm;
      if(_taskInfo._dinfo._predictor_transform == FrameTask.DataInfo.TransformType.STANDARDIZE) {
        newBetaDeNorm = fullBeta.clone();
        double norm = 0.0;        // Reverse any normalization on the intercept
        // denormalize only the numeric coefs (categoricals are not normalized)
        final int numoff = _taskInfo._dinfo.numStart();
        for( int i=numoff; i< fullBeta.length-1; i++ ) {
          double b = newBetaDeNorm[i]* _taskInfo._dinfo._normMul[i-numoff];
          norm += b* _taskInfo._dinfo._normSub[i-numoff]; // Also accumulate the intercept adjustment
          newBetaDeNorm[i] = b;
        }
        newBetaDeNorm[newBetaDeNorm.length-1] -= norm;
      } else
        newBetaDeNorm = null;
      GLMModel.setSubmodel(cmp, _taskInfo._dstKey, _currentLambda, newBetaDeNorm == null ? fullBeta : newBetaDeNorm, newBetaDeNorm == null ? null : fullBeta, (_iter + 1), System.currentTimeMillis() - _start_time, _taskInfo._dinfo.fullN() >= sparseCoefThreshold, val);
      return fullBeta;
    }

    /**
     * Computes the full gradient (gradient for all predictors) and checks line search condition (gradient has no NaNs/Infs) and the KKT conditions
     * for the underlying optimization problem. If some inactive columns violate the KKTs,
     * then they are added into the active set and solution is recomputed (rare), otherwise we just update the model in the K/V with this new solution
     * and finish.
     *
     * @param newBeta - computed solution
     * @param failedLineSearch - boolean flag if we're already comming from failed line-search (unable to proceed) - in that case line search is never performed.
     */
    protected void checkKKTAndComplete(final double [] newBeta, final boolean failedLineSearch){
      H2O.H2OCountedCompleter cmp = (H2O.H2OCountedCompleter)getCompleter();
      cmp.addToPendingCount(1);
      final double [] fullBeta;
      if(newBeta == null){
        fullBeta = MemoryManager.malloc8d(_taskInfo._dinfo.fullN()+1);
        fullBeta[fullBeta.length-1] = _taskInfo._params.linkInv(_taskInfo._ymu);
      } else
        fullBeta = expandVec(newBeta,_activeCols, _taskInfo._dinfo.fullN()+1);
      // now we need full gradient (on all columns) using this beta
      new GLMIterationTask(_jobKey, _taskInfo._dinfo, _taskInfo._params,false,true,true,fullBeta, _taskInfo._ymu,1.0/ _taskInfo._nobs, _taskInfo._thresholds, new H2O.H2OCallback<GLMIterationTask>(cmp) {
        @Override public String toString(){
          return "checkKKTAndComplete.Callback, completer = " + getCompleter() == null?"null":getCompleter().toString();
        }
        @Override
        public void callback(final GLMIterationTask glmt2) {
          // first check KKT conditions!
          final double [] grad = glmt2.gradient(_taskInfo._params._alpha[0], _currentLambda);
          if(ArrayUtils.hasNaNsOrInfs(grad)){
            if(!failedLineSearch) {
              LogInfo("Check KKT got NaNs. Invoking line search");
              _taskInfo._params._higher_accuracy = true;
              getCompleter().addToPendingCount(1);
              new GLMTask.GLMLineSearchTask(_jobKey, _activeData, _taskInfo._params, _lastResult._beta, contractVec(fullBeta,_activeCols), 1e-4, _taskInfo._ymu, _taskInfo._nobs, new LineSearchIteration(getCompleter())).asyncExec(_activeData._adaptedFrame);
              return;
            } else {
              // TODO: add warning and break the lambda search? Or throw Exception?
              LogInfo("got NaNs/Infs in gradient at lambda " + _currentLambda);
            }
          }
          // check the KKT conditions and filter data for the next lambda_value
          // check the gradient
          double[] subgrad = grad.clone();
          ADMMSolver.subgrad(_taskInfo._params._alpha[0], _currentLambda, fullBeta, subgrad);
          double err = GLM_GRAD_EPS;
          if (!failedLineSearch &&_activeCols != null) {
            for (int c : _activeCols)
              if (subgrad[c] > err) err = subgrad[c];
              else if (subgrad[c] < -err) err = -subgrad[c];
            int[] failedCols = new int[64];
            int fcnt = 0;
            for (int i = 0; i < grad.length - 1; ++i) {
              if (Arrays.binarySearch(_activeCols, i) >= 0) continue;
              if (subgrad[i] > err || -subgrad[i] > err) {
                if (fcnt == failedCols.length)
                  failedCols = Arrays.copyOf(failedCols, failedCols.length << 1);
                failedCols[fcnt++] = i;
              }
            }
            if (fcnt > 0) {
              final int n = _activeCols.length;
              int [] oldCols = _activeCols;
              _activeCols = Arrays.copyOf(_activeCols, _activeCols.length + fcnt);
              for (int i = 0; i < fcnt; ++i)
                _activeCols[n + i] = failedCols[i];
              if(_lastResult != null)
                _lastResult = new IterationInfo(_lastResult._iter, resizeVec(_lastResult._beta, _activeCols, oldCols, _taskInfo._dinfo.fullN() + 1),resizeVec(_lastResult._grad,_activeCols, oldCols, _taskInfo._dinfo.fullN() + 1), _lastResult._objval);
              Arrays.sort(_activeCols);
              LogInfo(fcnt + " variables failed KKT conditions check! Adding them to the model and continuing computation.(grad_eps = " + err + ", activeCols = " + (_activeCols.length > 100?"lost":Arrays.toString(_activeCols)));
              _activeData = _taskInfo._dinfo.filterExpandedColumns(_activeCols);
              // NOTE: tricky completer game here:
              // We expect 0 pending in this method since this is the end-point, ( actually it's racy, can be 1 with pending 1 decrement from the original Iteration callback, end result is 0 though)
              // while iteration expects pending count of 1, so we need to increase it here (Iteration itself adds 1 but 1 will be subtracted when we leave this method since we're in the callback which is called by onCompletion!
              // [unlike at the start of nextLambda call when we're not inside onCompletion]))
              getCompleter().addToPendingCount(1);
              new GLMIterationTask(_jobKey, _activeData, _taskInfo._params, true, true, true, contractVec(glmt2._beta, _activeCols), _taskInfo._ymu, 1.0/ _taskInfo._nobs, _taskInfo._thresholds, new Iteration(getCompleter())).asyncExec(_activeData._adaptedFrame);
              return;
            }
          }
          // update the state
          _taskInfo._beta = glmt2._beta;
          _taskInfo._gradient = glmt2.gradient(_taskInfo._params._alpha[0], _taskInfo._lastLambda);
          _taskInfo._iter = _iter;

          int diff = MAX_ITERATIONS_PER_LAMBDA - _iter + _taskInfo._iter;
          if(diff > 0)
            new Job.ProgressUpdate(diff).fork(_progressKey); // update progress
          setSubmodel(newBeta, glmt2._val,(H2O.H2OCountedCompleter)getCompleter().getCompleter());
        }
      }).asyncExec(_taskInfo._dinfo._adaptedFrame);
    }

    protected boolean needLineSearch(final GLMIterationTask glmt){ return needLineSearch(glmt,1);}
    protected boolean needLineSearch(final GLMIterationTask glmt, double step) {
      if(_taskInfo._params._family == Family.gaussian)
        return false;
      if(glmt._beta == null)
        return false;
      if (ArrayUtils.hasNaNsOrInfs(glmt._xy) || (glmt._grad != null && ArrayUtils.hasNaNsOrInfs(glmt._grad)) || (glmt._gram != null && glmt._gram.hasNaNsOrInfs()))
        return true;
      if(glmt._val != null && (glmt._val.residual_deviance > glmt._val.null_deviance))
        return true;
      if(glmt._val == null) // no validation info, no way to decide
        return false;
      return needLineSearch(glmt._beta, objval(glmt,_taskInfo._params._alpha[0], _currentLambda), step);
    }
    protected boolean needLineSearch(final double [] beta,double objval, double step){
      assert beta != null;
      if(Double.isNaN(objval))return true; // needed for gamma (and possibly others...)
      final double [] grad = _lastResult._grad;
      // line search
      double f_hat = 0;
      final double [] oldBeta = _lastResult == null?contractVec(_taskInfo._beta,_activeCols):_lastResult._beta;
      if(oldBeta == null) for(int i = 0; i < beta.length; ++i)
        f_hat += step*grad[i] * beta[i] + 0.5*beta[i]*beta[i];
      else for(int i = 0; i < beta.length; ++i) {
        double diff = (beta[i] - oldBeta[i]);
        f_hat += step * grad[i] * diff + .5*diff*diff;
      }
      f_hat = 1e-4*f_hat + _lastResult._objval;
      return objval > f_hat;
    }


    private static final boolean isSparse(water.fvec.Frame f) {
      int scount = 0;
      for(water.fvec.Vec v:f.vecs())
        if((v.nzCnt() << 3) > v.length())
          scount++;
      return (f.numCols() >> 1) < scount;
    }

    private GradientInfo adjustL2(GradientInfo ginfo, double [] coefs, double lambdaDiff) {
      for(int i = 0; i < coefs.length-1; ++i)
        ginfo._gradient[i] += lambdaDiff * coefs[i];
      return ginfo;
    }

    // GLMIterationTask(Key jobKey, DataInfo dinfo, GLMModel.GLMParameters glm, boolean computeGram, boolean validate, boolean computeGradient, double [] beta, double ymu, double reg, float [] thresholds, H2OCountedCompleter cmp) {
    private MRTask makeGLMTask(Key jobKey, DataInfo dinfo, GLMParameters params, boolean computeGram, boolean validate, boolean computeGradient, double [] beta) {
      return null;
    }
    @Override
    protected void compute2() {
      _start_time = System.currentTimeMillis();
      if(_currentLambda > _taskInfo._lambdaMax) {
        tryComplete();
        return; // no point doing anything, it's just the null model
      }
      _iter = _taskInfo._iter;
      LogInfo("starting computation of lambda = " + _currentLambda + ", previous lambda = " + _taskInfo._lastLambda);
      int [] activeCols = activeCols(_currentLambda, _taskInfo._lastLambda, _taskInfo._gradient);
      int n = activeCols == null?_taskInfo._dinfo.fullN():activeCols.length;
      if(n > _taskInfo._params._max_active_predictors)
        throw new TooManyPredictorsException();
      double [] beta = contractVec(_taskInfo._beta, _activeCols);
      _lastResult = new IterationInfo(_taskInfo._iter,beta,contractVec(_taskInfo._gradient,_activeCols), _taskInfo._objval);
      boolean LBFGS = _forceLBFGS;

      if(LBFGS) {
        if(_taskInfo._params._alpha[0] > 0 || _activeCols != null)
          throw H2O.unimpl();
        Log.info("current lambda = " + _currentLambda);
        GradientSolver solver = (_activeData._adaptedFrame.numCols() >= 100 || isSparse(_activeData._adaptedFrame))
          ?new GLMColBasedGradientSolver(_taskInfo._params,_activeData,_currentLambda,_taskInfo._ymu,_taskInfo._nobs)
          :new GLMGradientSolver(_taskInfo._params, _activeData, _currentLambda,_taskInfo._ymu, _taskInfo._nobs);
        if(beta == null) {
          beta = MemoryManager.malloc8d(_activeData.fullN() + 1);
          beta[beta.length-1] = _taskInfo._params.link(_taskInfo._ymu);
        }
        long t1 = System.currentTimeMillis();
        if(_taskInfo._lbfgs == null)
          _taskInfo._lbfgs = new L_BFGS();
        GradientInfo gOld = _taskInfo._gOld == null
          ?solver.getGradient(beta)
          :adjustL2(_taskInfo._gOld, beta, _currentLambda - _taskInfo._lastLambda);
        final int workInc = WORK_TOTAL/_taskInfo._params._lambda.length/_taskInfo._lbfgs.maxIter();
        L_BFGS.Result r = _taskInfo._lbfgs.solve(solver, beta, gOld, new ProgressMonitor() {
          @Override
          public boolean progress(GradientInfo ginfo) {
            update(workInc, _jobKey);
            // todo update the model here wo we can show intermediate results
            return Job.isRunning(_jobKey);
          }
        });
        long t2 = System.currentTimeMillis();
        Log.info("L_BFGS (k = " + _taskInfo._lbfgs.k() + ") done after " + r.iter + " iterations and " + ((t2-t1)/1000) + " seconds, objval = " + r.ginfo._objVal + ", penalty = " + (_currentLambda * .5 * ArrayUtils.l2norm2(beta,true)) + ",  gradient norm2 = " + (MathUtils.l2norm2(r.ginfo._gradient)));

        _taskInfo._gOld = r.ginfo;
        double [] newBeta = r.coefs;
        // update the state
        _taskInfo._beta = newBeta;
        _taskInfo._iter = (_iter += r.iter);
        setSubmodel(newBeta,null,this);
        tryComplete();
      } else // fork off ADMM iteration
        new GLMIterationTask(_jobKey, _activeData, _taskInfo._params, true, false, false, beta, _taskInfo._ymu, 1.0 / _taskInfo._nobs, _taskInfo._thresholds, new Iteration(this)).asyncExec(_activeData._adaptedFrame);
    }
    private class Iteration extends H2O.H2OCallback<GLMIterationTask> {
      public final long _iterationStartTime;
      final boolean _countIteration;
      final double _lineSearchStep;
      public Iteration(CountedCompleter cmp){ this(cmp,true,1.0);}
      public Iteration(CountedCompleter cmp, boolean countIteration,double lineSearchStep){
        super((H2O.H2OCountedCompleter)cmp);
        _lineSearchStep = lineSearchStep;
        _countIteration = countIteration;
        _iterationStartTime = System.currentTimeMillis(); }

      @Override public void callback(final GLMIterationTask glmt){
        if(_jobKey != null && !isRunning(_jobKey) )  throw new JobCancelledException();
        assert _activeCols == null || glmt._beta == null || glmt._beta.length == (_activeCols.length+1):LogInfo("betalen = " + glmt._beta.length + ", activecols = " + _activeCols.length);
        assert _activeCols == null || _activeCols.length == _activeData.fullN();
        assert getCompleter().getPendingCount() <= 1 : LogInfo("unexpected pending count, expected <=  1, got " + getCompleter().getPendingCount()); // will be decreased by 1 after we leave this callback
        if (_countIteration) ++_iter;
        long callbackStart = System.currentTimeMillis();
        if(needLineSearch(glmt,_lineSearchStep)){
          getCompleter().addToPendingCount(1);
          LogInfo("invoking line search");
          double [] oldBeta = _lastResult._beta;
          if(oldBeta == null) {
            oldBeta = MemoryManager.malloc8d(_taskInfo._dinfo.fullN() + 1);
            oldBeta[oldBeta.length-1] = _taskInfo._params.link(_taskInfo._ymu);
          }
          new GLMTask.GLMLineSearchTask(_jobKey,_activeData, _taskInfo._params, oldBeta, glmt._beta,1e-4, _taskInfo._ymu, _taskInfo._nobs, new LineSearchIteration(getCompleter())).asyncExec(_activeData._adaptedFrame);
          return;
        }
        if(glmt._newThresholds != null) {
          _taskInfo._thresholds = ArrayUtils.join(glmt._newThresholds[0], glmt._newThresholds[1]);
          Arrays.sort(_taskInfo._thresholds);
        }
        double gerr = Double.NaN;
        if (glmt._val != null && glmt._computeGradient) { // check gradient
          _lastResult = new IterationInfo(_iter,glmt._beta,glmt.gradient(_taskInfo._params._alpha[0], _currentLambda),objval(glmt,_taskInfo._params._alpha[0],_currentLambda));
          double [] grad = _lastResult._grad.clone();
          ADMMSolver.subgrad(_taskInfo._params._alpha[0], _currentLambda, glmt._beta, grad);
          gerr = 0;
          for (double d : grad)
            if (d > gerr) gerr = d;
            else if (d < -gerr) gerr = -d;
          if(gerr <= GLM_GRAD_EPS){
            LogInfo("converged by reaching small enough gradient, with max |subgradient| = " + gerr  /* + ", coefs = " + Arrays.toString(glmt._beta) */);
            checkKKTAndComplete(glmt._beta,false);
            return;
          }
        }
        final double [] newBeta = MemoryManager.malloc8d(glmt._xy.length);
        long t1 = System.currentTimeMillis();
        ADMMSolver slvr = new ADMMSolver(_currentLambda, _taskInfo._params._alpha[0], GLM_GRAD_EPS, _addedL2);
        slvr.solve(glmt._gram,glmt._xy,glmt._yy,newBeta, _currentLambda * _taskInfo._params._alpha[0]);
        if(_lineSearchStep < 1){
          if(glmt._beta != null)
            for(int i = 0; i < newBeta.length; ++i)
              newBeta[i] = glmt._beta[i]*(1-_lineSearchStep) + _lineSearchStep*newBeta[i];
          else
            for(int i = 0; i < newBeta.length; ++i)
              newBeta[i] *= _lineSearchStep;
        }
        // print all info about iteration
        LogInfo("Gram computed in " + (callbackStart - _iterationStartTime) + "ms, " + (Double.isNaN(gerr)?"":"gradient = " + gerr + ",") + ", step = " + _lineSearchStep + ", ADMM: " + slvr.iterations + " iterations, " + (System.currentTimeMillis() - t1) + "ms (" + slvr.decompTime + "), subgrad_err=" + slvr.gerr);
        if (slvr._addedL2 > _addedL2) LogInfo("added " + (slvr._addedL2 - _addedL2) + "L2 penalty");
        new Job.ProgressUpdate(1).fork(_progressKey); // update progress
        _addedL2 = slvr._addedL2;
        if (ArrayUtils.hasNaNsOrInfs(newBeta)) {
          throw new RuntimeException(LogInfo("got NaNs and/or Infs in beta"));
        } else {
          final double bdiff = beta_diff(glmt._beta, newBeta);
          if (_taskInfo._params._family == Family.gaussian || bdiff < beta_epsilon || _iter >= _taskInfo._max_iter) { // Gaussian is non-iterative and gradient is ADMMSolver's gradient => just validate and move on to the next lambda_value
            int diff = (int) Math.log10(bdiff);
            int nzs = 0;
            for (int i = 0; i < newBeta.length; ++i)
              if (newBeta[i] != 0) ++nzs;
            LogInfo("converged (reached a fixed point with ~ 1e" + diff + " precision), got " + nzs + " nzs");
            checkKKTAndComplete(newBeta, false);
            return;
          } else { // not done yet, launch next iteration
            if (glmt._beta != null)
              setSubmodel(glmt._beta, glmt._val, (H2O.H2OCountedCompleter) getCompleter().getCompleter()); // update current intermediate result
            final boolean validate = _taskInfo._params._higher_accuracy || (_iter % 5) == 0;
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(_jobKey,_activeData,glmt._glm, true, validate, validate, newBeta, _taskInfo._ymu,1.0/ _taskInfo._nobs, _taskInfo._thresholds, new Iteration(getCompleter(),true,Math.min(1,2*_lineSearchStep))).asyncExec(_activeData._adaptedFrame);
          }
        }
      }
    }
    private class LineSearchIteration extends H2O.H2OCallback<GLMLineSearchTask> {
      LineSearchIteration(CountedCompleter cmp){super((H2O.H2OCountedCompleter)cmp); }
      @Override public void callback(final GLMTask.GLMLineSearchTask glmt) {
        assert getCompleter().getPendingCount() <= 1:"unexpected pending count, expected 1, got " + getCompleter().getPendingCount();
        double step = 0.5;
        for(int i = 0; i < glmt._glmts.length; ++i){
          if(!needLineSearch(glmt._glmts[i],step)){
            LogInfo("line search: found admissible step = " + step + ",  objval = " + objval(glmt._glmts[i],_taskInfo._params._alpha[0],_currentLambda));
            _taskInfo._params._higher_accuracy = true;
            getCompleter().addToPendingCount(1);
            new GLMIterationTask(_jobKey,_activeData, _taskInfo._params,true,true,true,glmt._glmts[i]._beta, _taskInfo._ymu,1.0/ _taskInfo._nobs, _taskInfo._thresholds, new Iteration(getCompleter(),false,step)).asyncExec(_activeData._adaptedFrame);
            return;
          }
          step *= 0.5;
        } // no line step worked converge
        if(!_taskInfo._params._higher_accuracy){ // start from scratch
          _taskInfo._params._higher_accuracy = true;
          int add2iter = (_iter - _taskInfo._iter);
          LogInfo("Line search failed to progress, rerunning current lambda from scratch with high accuracy on, adding " + add2iter + " to max iterations");
          _taskInfo._max_iter += add2iter;
          getCompleter().addToPendingCount(1);
          new GLMIterationTask(_jobKey,_activeData, _taskInfo._params,true,true,true,contractVec(_taskInfo._beta,_activeCols), _taskInfo._ymu,1.0/ _taskInfo._nobs, _taskInfo._thresholds, new Iteration(getCompleter(),false,1)).asyncExec(_activeData._adaptedFrame);
          return;
        }
        LogInfo("Line search did not find feasible step, converged.");
        checkKKTAndComplete(_lastResult._beta,true);
      }
    }


  }

  /**
   * Contains implementation of the glm algo.
   * It's a DTask so it can be computed on other nodes (to distributed single node part of the computation).
   */
  public final class GLMDriver extends DTask<GLMDriver> {
    final DataInfo _dinfo;
    transient ArrayList<DataInfo> _foldInfos = new ArrayList<DataInfo>();

    double [] lambdas;
    final GLMTaskInfo[] _state;
    int             _lambdaId;
    int   _maxLambda;
    transient AtomicBoolean _gotException = new AtomicBoolean();

    public GLMDriver(H2OCountedCompleter cmp, DataInfo dinfo){
      super(cmp);
      _dinfo = dinfo;
      _state = _parms._n_folds > 1?new GLMTaskInfo[_parms._n_folds +1]:new GLMTaskInfo[1];
    }

    private double [] nullBeta(DataInfo dinfo, GLMParameters params, double ymu){
      double [] beta = MemoryManager.malloc8d(dinfo.fullN()+1);
      beta[beta.length-1] = params.linkInv(ymu);
      return beta;
    }

    private void doCleanup(){
      DKV.remove(_dinfo._key);
      for(DataInfo dinfo:_foldInfos)
        DKV.remove(dinfo._key);
    }
    @Override public boolean onExceptionalCompletion(final Throwable ex, CountedCompleter cc){
      doCleanup();
      if(!_gotException.getAndSet(true)){
        if(ex instanceof TooManyPredictorsException){
          // TODO add a warning
          _maxLambda = _lambdaId;
          this.tryComplete();
          return false;
        }
        new RemoveCall(null, _dest).invokeTask();
        return true;
      }
      return false;
    }
    @Override public void onCompletion(CountedCompleter cc){
      doCleanup();
      H2OCountedCompleter cmp = (H2OCountedCompleter)getCompleter();
      cmp.addToPendingCount(1);
      new FinalizeAndUnlockTsk(cmp, _dest, _key).fork();
    }
    @Override
    protected void compute2() {
      if(_parms._alpha.length > 1){ // just fork off grid search
        return;
      }
      // compute lambda max
      // if this is cross-validated task, don't do actual computation,
      // just fork off the nfolds+1 tasks and wait for the results

      if(_parms._nlambdas == -1)_parms._nlambdas = 100;
      if(_parms._lambda_search && _parms._nlambdas <= 1)
        throw new IllegalArgumentException("GLM2(" + _dest + ") nlambdas must be > 1 when running with lambda search.");
      Futures fs = new Futures();
      new YMUTask(_key, _dinfo._key, _parms._n_folds,new H2O.H2OCallback<YMUTask>(this) {
        @Override
        public String toString(){
          return "YMUTask callback. completer = " + getCompleter() != null?"null":getCompleter().toString();
        }
        @Override
        public void callback(final YMUTask ymut) {
          if (ymut._ymin == ymut._ymax)
            throw new IllegalArgumentException("GLM2(" + _dest + "): attempted to run with constant response. Response == " + ymut._ymin + " for all rows in the training set.");
          final double gYmu;
          final long nobs;
          boolean skipNAs = true;
          final double iceptAdjust;
          if((double)ymut.nobs()/_dinfo._adaptedFrame.numRows() < .75){
            skipNAs = false;
            gYmu = _dinfo._adaptedFrame.lastVec().mean();
            nobs = _dinfo._adaptedFrame.numRows();
          } else {
            gYmu = ymut.ymu();
            nobs = ymut.nobs();
          }
          if(_parms._family == Family.binomial && _parms._prior != -1 && _parms._prior != gYmu && !Double.isNaN(_parms._prior)) {
            double ratio = _parms._prior / gYmu;
            double pi0 = 1, pi1 = 1;
            if (ratio > 1) {
              pi1 = 1.0 / ratio;
            } else if (ratio < 1) {
              pi0 = ratio;
            }
            iceptAdjust = Math.log(pi0 / pi1);
          } else {
            _parms._prior = gYmu;
            iceptAdjust = 0;
          }
          H2O.H2OCountedCompleter cmp = (H2O.H2OCountedCompleter)getCompleter();
          cmp.addToPendingCount(1);
          new LMAXTask(_key, _dinfo, _parms, gYmu,nobs, ModelUtils.DEFAULT_THRESHOLDS,new H2O.H2OCallback<LMAXTask>(cmp){
            @Override
            public String toString(){
              return "LMAXTask callback. completer = " + (getCompleter() != null?"NULL":getCompleter().toString());
            }
            @Override public void callback(final LMAXTask gLmax){
              // public GLMModel(Key selfKey, String[] names, String[][] domains, GLMParameters parms, GLMOutput output) {
              GLMOutput glmOutput = new GLMOutput(GLM.this,_dinfo,_parms._family == Family.binomial);
              String warning = null;

              if(_parms._lambda_search) {
                assert !Double.isNaN(gLmax.lmax()) : "running lambda_value search, but don't know what is the lambda_value max!";
                if (_parms._lambda_min_ratio == -1)
                  _parms._lambda_min_ratio = nobs > 25 * _dinfo.fullN() ? 1e-4 : 1e-2;
                  final double d = Math.pow(_parms._lambda_min_ratio, 1.0 / (_parms._nlambdas - 1));
                  lambdas = new double[_parms._nlambdas];
                  lambdas[0] = gLmax.lmax();
                  if (_parms._nlambdas == 1)
                    throw new IllegalArgumentException("Number of lambdas must be > 1 when running with lambda_search!");
                  for (int i = 1; i < lambdas.length; ++i)
                    lambdas[i] = lambdas[i - 1] * d;
              } else {
                if(_parms._lambda == null || _parms._lambda.length == 0)
                  lambdas = new double[]{1e-2*gLmax.lmax()};
                else
                  lambdas = _parms._lambda;
                int i = 0;
                while(i < lambdas.length && lambdas[i] >= gLmax.lmax())++i;
                if(i == lambdas.length)
                  throw new IllegalArgumentException("Given lambda(s) are all > lambda_max = " + gLmax.lmax() + ", have nothing to run with. lambda = " + Arrays.toString(lambdas));
                if(i > 0) {
                  warning = "Removed " + i + " lambdas greater than lambda_max.";
                }
                lambdas = ArrayUtils.append(new double[]{gLmax.lmax()},Arrays.copyOfRange(lambdas,i,lambdas.length));
              }
              double nextLambda = lambdas[1];
              if(lambdas.length > 1)
                glmOutput.addNullSubmodel(gLmax.lmax(), _parms.link(gYmu), gLmax._val);
              _maxLambda = lambdas.length;
              GLMModel model = new GLMModel(_dest, _parms, glmOutput, _dinfo, gYmu,gLmax.lmax(),nobs);
              if(warning != null)
                model.addWarning(warning);
              model.delete_and_lock(_key);
              final double lmax = gLmax.lmax();
              _state[0] = new GLMTaskInfo(_dest,_dinfo,_parms,gLmax._nobs,gLmax._ymu,lmax,lmax,null,gLmax.gradient(_parms._alpha[0],lmax),objval(gLmax,_parms._alpha[0],gLmax.lmax()));
              getCompleter().addToPendingCount(1);
              if(_parms._n_folds > 1){
                final H2OCountedCompleter cmp = new H2OCallback((H2OCountedCompleter)getCompleter()) {
                  @Override
                  public void callback(H2OCountedCompleter h2OCountedCompleter) {
                    GLMLambdaTask [] tasks = new GLMLambdaTask[_state.length];

                    for(int i = 0; i < tasks.length; ++i)
                      tasks[i] = new GLMLambdaTask(null, _key,_progressKey,_state[i],lambdas[_lambdaId], _parms._solver == Solver.L_BFGS);
                    // now we have computed lmax for all n_folds model and solution for global lmax (lmax on the whole dataset) for all n_folds
                    // just start tasks to compute the first lambda in parallel for all n_folds.
                    getCompleter().addToPendingCount(1);
                    new ParallelTasks(new LambdaSearchIteration((H2OCountedCompleter)getCompleter(), _parms._solver == Solver.L_BFGS),tasks).fork();
                  }
                };
                cmp.addToPendingCount(_state.length-2);
                for(int i = 1; i < _state.length; ++i){
                  final int fi = i;
                  final GLMParameters params = (GLMParameters)_parms.clone();
                  params._n_folds = 0;
                  final DataInfo dinfo = _dinfo.getFold(i-1,_parms._n_folds);
                  _foldInfos.add(dinfo);
                  DKV.put(dinfo._key, dinfo);
                  Log.info("inserted dinfo for fold " + i + " under key " + dinfo._key);
                  if(i != 0){
                    // public LMAXTask(Key jobKey, DataInfo dinfo, GLMModel.GLMParameters glm, double ymu, long nobs, double alpha, float [] thresholds, H2OCountedCompleter cmp) {
                    new LMAXTask(_key,dinfo,params,ymut.ymu(fi-1),ymut.nobs(fi-1), ModelUtils.DEFAULT_THRESHOLDS,new H2OCallback<LMAXTask>(cmp) {
                      @Override
                      public String toString(){
                        return "Xval LMAXTask callback., completer = " + getCompleter() == null?"null":getCompleter().toString();
                      }
                      @Override
                      public void callback(LMAXTask lLmax) {
                        // long nobs, double ymu, double lmax, double [] beta, double [] gradient
                        final double lmax = lLmax.lmax();
                        Key dstKey = Key.make(_dest.toString() + "_xval_" + fi, (byte)1, Key.HIDDEN_USER_KEY, true, H2O.SELF);
                        _state[fi] = new GLMTaskInfo(dstKey,dinfo,params,lLmax._nobs,lLmax._ymu,lLmax.lmax(),gLmax.lmax(),nullBeta(dinfo,params,lLmax._ymu),lLmax.gradient(_parms._alpha[0],lmax),objval(lLmax,_parms._alpha[0],lLmax.lmax()));
                        assert DKV.get(dinfo._key) != null;
                        new GLMModel(dstKey, params, new GLMOutput(GLM.this,dinfo,_parms._family == Family.binomial), dinfo, lLmax._ymu, lmax, nobs).delete_and_lock(_key);
                        if(lLmax.lmax() > gLmax.lmax()){
                          getCompleter().addToPendingCount(1);
                          // lambda max for this n_fold is > than global lambda max -> it has non-trivial solution for global lambda max, need to compute it first.
                          new GLMLambdaTask((H2OCountedCompleter)getCompleter(), _key,_progressKey,_state[fi],gLmax.lmax(), _parms._solver == Solver.L_BFGS).fork();
                        }
                      }
                    }).asyncExec(dinfo._adaptedFrame);
                  }
                }
              } else {
                new GLMLambdaTask(new LambdaSearchIteration((H2OCountedCompleter) getCompleter(), _parms._solver == Solver.L_BFGS), _key, _progressKey, _state[0], lambdas[++_lambdaId], _parms._solver == Solver.L_BFGS).fork();
              }
            }
          }).asyncExec(_dinfo._adaptedFrame);
        }
      }).asyncExec(_dinfo._adaptedFrame);
    }

    private class LambdaSearchIteration extends H2O.H2OCallback {
      final boolean _forceLBFGS;
      public LambdaSearchIteration(H2OCountedCompleter cmp, boolean forceLBFGS){super(cmp); _forceLBFGS = forceLBFGS; }

      @Override
      public void callback(H2OCountedCompleter h2OCountedCompleter) {
        double currentLambda = lambdas[_lambdaId];
        if(_parms._n_folds > 1){
          // copy the state over
          ParallelTasks<GLMLambdaTask> t = (ParallelTasks<GLMLambdaTask>)h2OCountedCompleter;
          for(int i = 0; i < t._tasks.length; ++i)
            _state[i] = t._tasks[i]._taskInfo;
          // launch xval-task to compute validations of xval models
          // getCompleter().addToPendingCount(1);
          // TODO ...
        }
        // now launch the next lambda

        if(++_lambdaId  < _maxLambda){
          getCompleter().addToPendingCount(1);
          double nextLambda = lambdas[_lambdaId];
          if(_parms._n_folds > 1){
            GLMLambdaTask [] tasks = new GLMLambdaTask[_state.length];
            H2OCountedCompleter cmp = new LambdaSearchIteration((H2OCountedCompleter)getCompleter(), _forceLBFGS);
            cmp.addToPendingCount(tasks.length-1);
            for(int i = 0; i < tasks.length; ++i) {
              _state[i]._lastLambda = currentLambda;
              tasks[i] = new GLMLambdaTask(cmp, _key, _progressKey, _state[i], nextLambda, false);
            }
            new ParallelTasks(new LambdaSearchIteration((H2OCountedCompleter)getCompleter(), _forceLBFGS),tasks).fork();
          } else {
            _state[0]._lastLambda = currentLambda;
            new GLMLambdaTask(new LambdaSearchIteration((H2OCountedCompleter) getCompleter(), _forceLBFGS), _key, _progressKey, _state[0], nextLambda, _forceLBFGS).fork();
          }
        }
      }
    }
  }
  private static final double beta_diff(double[] b1, double[] b2) {
    if(b1 == null)return Double.MAX_VALUE;
    double res = b1[0] >= b2[0]?b1[0] - b2[0]:b2[0] - b1[0];
    for( int i = 1; i < b1.length; ++i ) {
      double diff = b1[i] - b2[i];
      if(diff > res)
        res = diff;
      else if( -diff > res)
        res = -diff;
    }
    return res;
  }
  private static final double [] expandVec(double [] beta, final int [] activeCols, int fullN){
    assert beta != null;
    if (activeCols == null) return beta;
    double[] res = MemoryManager.malloc8d(fullN);
    int i = 0;
    for (int c : activeCols)
      res[c] = beta[i++];
    res[res.length - 1] = beta[beta.length - 1];
    return res;
  }
  private static final double [] contractVec(double [] beta, final int [] activeCols){
    if(beta == null)return null;
    if(activeCols == null)return beta.clone();
    double [] res = MemoryManager.malloc8d(activeCols.length+1);
    int i = 0;
    for(int c:activeCols)
      res[i++] = beta[c];
    res[res.length-1] = beta[beta.length-1];
    return res;
  }
  private static final double [] resizeVec(double[] beta, final int[] activeCols, final int[] oldActiveCols, int fullN){
    if(beta == null || Arrays.equals(activeCols,oldActiveCols))return beta;
    double [] full = expandVec(beta, oldActiveCols,fullN);
    if(activeCols == null)return full;
    return contractVec(full,activeCols);
  }


  protected static double l1norm(double[] beta){
    if(beta == null)return 0;
    double l1 = 0;
    for (int i = 0; i < beta.length-1; ++i)
      l1 += beta[i] < 0?-beta[i]:beta[i];
    return l1;
  }

  private static double penalty(double [] beta, double alpha, double lambda){
    return lambda*(alpha*l1norm(beta) + .5*(1-alpha)*ArrayUtils.l2norm(beta, true));
  }
  private static double  objval(GLMIterationTask glmt, double alpha, double lambda){
    return glmt._val.residual_deviance / glmt._nobs + penalty(glmt._beta,alpha,lambda);
  }




  public final static class GLMColBasedGradientSolver extends GradientSolver {
    final Key _jobKey = null;
    final GLMParameters _glmp;
    final DataInfo _dinfo;
    final double _ymu;
    final double _lambda;
    final long _nobs;

    public GLMColBasedGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, long nobs){
      _glmp = glmp;
      _dinfo = dinfo;
      _ymu = ymu;
      _nobs = nobs;
      _lambda = lambda;
    }

    @Override
    public GradientInfo[] getGradient(double[][] betas) {

      ColGradientTask gt = new ColGradientTask(_dinfo,_glmp,betas,1.0/_nobs).doAll(_dinfo._adaptedFrame);
      GradientInfo [] ginfos = new GradientInfo[betas.length];
      for(int i = 0; i < ginfos.length; ++i) {
        for(int j = 0; j < betas[i].length-1; ++j)
          gt._gradient[i][j] += _lambda*betas[i][j];
        ginfos[i] = new GradientInfo(gt._objVals[i] + .5 * _lambda * ArrayUtils.l2norm2(betas[i], true), gt._gradient[i]);
      }
      return ginfos;
    }
  }

  public final static class GLMGradientInfo extends GradientInfo {
    public final GLMValidation _val;
    public GLMGradientInfo(GLMIterationTask t, double lambda) {
      super(t._val.residualDeviance()/t._nobs + .5 * lambda * ArrayUtils.l2norm2(t._beta, true), t.gradient(0,lambda));
      _val = t._val;
    }
  }

  public final static class GLMGradientSolver extends GradientSolver {
    final Key _jobKey = null;
    final GLMParameters _glmp;
    final DataInfo _dinfo;
    final double _ymu;
    final double _lambda;
    final long _nobs;

    public GLMGradientSolver(GLMParameters glmp, DataInfo dinfo, double lambda, double ymu, long nobs){
      _glmp = glmp;
      _dinfo = dinfo;
      _ymu = ymu;
      _nobs = nobs;
      _lambda = lambda;
    }


    @Override
    public GradientInfo[] getGradient(double[][] betas) {
      final double reg = 1.0/_nobs;
      GLMIterationTask [] glmts =  new GLMLineSearchTask(_jobKey,_dinfo,_glmp,betas,_ymu,_nobs,null).doAll(_dinfo._adaptedFrame)._glmts;
      GradientInfo [] ginfos = new GradientInfo[glmts.length];
      for(int i = 0; i < ginfos.length; ++i)
        ginfos[i] = new GLMGradientInfo(glmts[i],_lambda);
      return ginfos;
    }
  }

}
