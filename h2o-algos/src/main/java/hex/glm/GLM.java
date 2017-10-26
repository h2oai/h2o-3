package hex.glm;

import hex.*;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLMModel.*;
import hex.optimization.OptimizationUtils;
import jsr166y.CountedCompleter;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.util.*;
import water.util.ArrayUtils;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Arrays;


/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 */
public class GLM extends ModelBuilder<GLMModel,GLMParameters,GLMOutput> {
  private boolean _cv; // flag signalling this is MB for one of the fold-models during cross-validation
  static NumberFormat lambdaFormatter = new DecimalFormat(".##E0");
  String _generatedWeights = null;

  public GLM(boolean startup_once) {
    super(new GLMParameters(), startup_once);
  }

  public GLM(GLMModel.GLMParameters parms) {
    super(parms);
    init(false);
  }

  public GLM(GLMModel.GLMParameters parms, Key<GLMModel> dest) {
    super(parms, dest);
    init(false);
  }

  // helper function
  static double y_log_y(double y, double mu) {
    if(y == 0)return 0;
    if(mu < Double.MIN_NORMAL) mu = Double.MIN_NORMAL;
    return y * Math.log(y / mu);
  }

  public boolean isSupervised() {
    return true;
  }

  @Override
  public ModelCategory[] can_build() {
    return new ModelCategory[]{
        ModelCategory.Regression,
        ModelCategory.Binomial,
    };
  }

  @Override
  public boolean havePojo() {
    return true;
  }

  @Override
  public boolean haveMojo() {
    return true;
  }

  private boolean _isXvalMain = false;
  private double[] _xval_test_deviances;
  private double[] _xval_test_sd;

  /**
   * GLM implementation of N-fold cross-validation.
   * We need to compute the sequence of lambdas for the main model so the folds share the same lambdas.
   * We also want to set the _cv flag so that the dependent jobs know they're being run withing CV (so e.g. they do not unlock the models in the end)
   *
   * (builds N+1 models, all have train+validation metrics, the main model has N-fold cross-validated validation metrics)
   */
  @Override
  public void computeCrossValidation() {
    // init computes global list of lambdas
    init(true);
    _cv = true;
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
    super.computeCrossValidation();
  }

  /**
   * If run with lambda search, we need to take extra action performed after cross-val models are built.
   * Each of the folds have been computed with ots own private validation datasetd and it performed early stopping based on it.
   * => We need to:
   * 1. compute cross-validated lambda estimate
   * 2. set the lambda estimate too all n-folds models (might require extra model fitting if the particular model stopped too early!)
   * 3. compute cross-validated scoring history (cross-validated deviance standard error per lambda)
   * 4. unlock the n-folds models (they are changed here, so the unlocking happens here)
   */
  @Override
  public void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders) {
    if (_parms._lambda_search) {
      _xval_test_deviances = new double[_parms._lambda.length];
      _xval_test_sd = new double[_parms._lambda.length];
      double bestTestDev = Double.POSITIVE_INFINITY;
      int lmin_max = 0;
      if(_parms._early_stopping)
        for (ModelBuilder cvModelBuilder : cvModelBuilders) {
          GLM g = (GLM) cvModelBuilder;
          lmin_max = Math.max(lmin_max, g._model._output._best_lambda_idx);
        }
      else lmin_max = _parms._lambda.length-1;
      int lidx = 0;
      int bestId = 0;
      int cnt = 0;
      for (; lidx < lmin_max; ++lidx) {
        double testDev = 0;
        for (ModelBuilder cvModelBuilder : cvModelBuilders) {
          GLM g = (GLM) cvModelBuilder;
          double x = _parms._lambda[lidx];
          Submodel sm = g._model._output.getSubmodel(x);
          if (sm == null) sm = g.computeNext(x);
          testDev += sm.devianceTest;
        }
        double testDevAvg = testDev / cvModelBuilders.length;
        double testDevSE = 0;
        // compute deviance standard error
        for (ModelBuilder cvModelBuilder : cvModelBuilders) {
          GLM g = (GLM) cvModelBuilder;
          double x = _parms._lambda[lidx];
          double diff = testDevAvg - (g._model._output.getSubmodel(x).devianceTest);
          testDevSE += diff * diff;
        }
        _xval_test_sd[lidx] = Math.sqrt(testDevSE / ((cvModelBuilders.length - 1) * cvModelBuilders.length));
        _xval_test_deviances[lidx] = testDevAvg;
        if (testDevAvg < bestTestDev) {
          bestTestDev = testDevAvg;
          bestId = lidx;
        }
        // early stopping - no reason to move further if we're overfitting
        if (_parms._early_stopping && (testDevAvg > bestTestDev && ++cnt == 3)) {
          lmin_max = lidx;
          break;
        }
      }
      for (ModelBuilder cvModelBuilder : cvModelBuilders) {
        GLM g = (GLM) cvModelBuilder;
        if (g._toRemove != null)
          for (Key k : g._toRemove)
            Keyed.remove(k);
      }
      _parms._lambda = Arrays.copyOf(_parms._lambda, lmin_max + 1);
      _xval_test_deviances = Arrays.copyOf(_xval_test_deviances, lmin_max + 1);
      _xval_test_sd = Arrays.copyOf(_xval_test_sd, lmin_max + 1);
      for (ModelBuilder cvModelBuilder : cvModelBuilders) {
        GLM g = (GLM) cvModelBuilder;
        g.scoreAndUpdateModel(bestId);
      }
      double bestDev = _xval_test_deviances[bestId];
      double bestDev1se = bestDev + _xval_test_sd[bestId];
      int bestId1se = bestId;
      while (bestId1se > 0 && _xval_test_deviances[bestId1se - 1] <= bestDev1se)
        --bestId1se;
      _model._output._lambda_1se = bestId1se;
      _model._output._best_lambda_idx = bestId;
    }
    for (ModelBuilder cvModelBuilder : cvModelBuilders) {
      GLM g = (GLM) cvModelBuilder;
      g._model.unlock(_job);
    }
    _isXvalMain = true;
    _cv = false;
  }

  private void addWarning(String s) {
    Log.warn(LogMsg(s));
    _model.addWarning(s);
  }
  private transient ScoringHistory _sc;
  private double _lmax;
  private transient long _nobs;
  private transient GLMModel _model;

  @Override
  public int nclasses() {
    if (_parms._family == Family.multinomial)
      return _nclass;
    if (_parms._family == Family.binomial || _parms._family == Family.quasibinomial)
      return 2;
    return 1;
  }

  protected boolean computePriorClassDistribution() {
    return _parms._family == Family.multinomial;
  }

  private double[] _responseMean;
  private double[] _nullBeta;
  private GLMGradientInfo _nullGradient;

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    hide("_balance_classes", "Not applicable since class balancing is not required for GLM.");
    hide("_max_after_balance_size", "Not applicable since class balancing is not required for GLM.");
    hide("_class_sampling_factors", "Not applicable since class balancing is not required for GLM.");
    _parms.validate(this);
    if (_response != null) {
      if (!isClassifier() && _response.isCategorical())
        error("_response", H2O.technote(2, "Regression requires numeric response, got categorical."));
      switch (_parms._family) {
        case binomial:
          if (!_response.isBinary() && _nclass != 2)
            error("_family", H2O.technote(2, "Binomial requires the response to be a 2-class categorical or a binary column (0/1)"));
          break;
        case multinomial:
          if (_nclass <= 2)
            error("_family", H2O.technote(2, "Multinomial requires a categorical response with at least 3 levels (for 2 class problem use family=binomial."));
          break;
        case poisson:
          if (_nclass != 1) error("_family", "Poisson requires the response to be numeric.");
          if (_response.min() < 0)
            error("_family", "Poisson requires response >= 0");
          if (!_response.isInt())
            warn("_family", "Poisson expects non-negative integer response, got floats.");
          break;
        case gamma:
          if (_nclass != 1) error("_distribution", H2O.technote(2, "Gamma requires the response to be numeric."));
          if (_response.min() <= 0) error("_family", "Gamma requires positive respone");
          break;
        case tweedie:
          if (_nclass != 1) error("_family", H2O.technote(2, "Tweedie requires the response to be numeric."));
          break;
        case quasibinomial:
          if (_nclass != 1) error("_family", H2O.technote(2, "Quasi_binomial requires the response to be numeric."));
          break;
        case gaussian:
//          if (_nclass != 1) error("_family", H2O.technote(2, "Gaussian requires the response to be numeric."));
          break;
        default:
          error("_family", "Invalid distribution: " + _parms._distribution);
      }
    }
    if (expensive) {
      if (error_count() > 0) return;
      _sc = new ScoringHistory();
      _train.bulkRollups(); // make sure we have all the rollups computed in parallel
      _sc = new ScoringHistory();
      if (_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0)
        _parms._use_all_factor_levels = true;
      if (_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
      _dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, _parms._missing_values_handling == DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip, _parms._missing_values_handling == DeepLearningModel.DeepLearningParameters.MissingValuesHandling.MeanImputation, false, hasWeightCol(), hasOffsetCol(), hasFoldCol(), _parms._interactions);
      // skipping extra rows? (outside of weights == 0)
      boolean skippingRows = (_parms._missing_values_handling == DeepLearningModel.DeepLearningParameters.MissingValuesHandling.Skip && _train.hasNAs());
      if (hasWeightCol() || skippingRows) { // need to re-compute means and sd
        if (skippingRows) {
          Vec wc = _weights == null ? _dinfo._adaptedFrame.anyVec().makeCon(1) : _weights.makeCopy();
          _dinfo.setWeights(_generatedWeights = "__glm_gen_weights", wc);
        }
        GLMTask.YMUTask ymt = new GLMTask.YMUTask(_dinfo, _parms._family == Family.multinomial ? nclasses() : 1, skippingRows, skippingRows, true, false).doAll(_dinfo._adaptedFrame);
        if (ymt.wsum() == 0)
          throw new IllegalArgumentException("No rows left in the dataset after filtering out rows with missing values. Ignore columns with many NAs or impute your missing values prior to calling glm.");
        Log.info(LogMsg("using " + ymt.nobs() + " nobs out of " + _dinfo._adaptedFrame.numRows() + " total"));
        // if sparse data, need second pass to compute variance
        _glmf = new GLMWeightsFun(_parms);
        _nobs = ymt.nobs();
        _alpha = _parms._alpha[0];
        _P = (_dinfo.fullN()+1)*nclasses();
        _objReg = _parms._obj_reg == -1
            ?1.0 / ymt.wsum()
            :_parms._obj_reg;
        _hasIntercept = _parms._intercept && _dinfo._response_transform != DataInfo.TransformType.STANDARDIZE;
        if (!_parms._stdOverride)
          _dinfo.updateWeightedSigmaAndMean(ymt.predictorSDs(), ymt.predictorMeans());
        _responseMean = ymt._yMu;
        int [] badCols = ymt.badCols();
        if(badCols.length > 0) {
          // remove bad cols and log
          _dinfo = _dinfo.removeCols(badCols);
        }
      } else {
        _nobs = _train.numRows();
        if (_parms._obj_reg == -1)
          _parms._obj_reg = 1.0 / _nobs;
        _responseMean = _parms._family == Family.multinomial ? _priorClassDist : new double[]{_train.lastVec().mean()};
      }
      BetaConstraint bc = (_parms._beta_constraints != null) ? new BetaConstraint(this, _parms._beta_constraints.get()) : new BetaConstraint(this);
      if ((bc.hasBounds() || bc.hasProximalPenalty()) && _parms._compute_p_values)
        error("_compute_p_values", "P-values can not be computed for constrained problems");
      if (bc.hasBounds())
        _parms._early_stopping = false; // PUBDEV-4641: early stopping does not work correctly with non-negative option
      _bc = bc;
      // compute beta for the null model, compute gradient, lambda_max
      _nullBeta = MemoryManager.malloc8d(_P);
      if(_hasIntercept)
        for(int i = 0; i < _responseMean.length; ++i) {
          int id = (_dinfo.fullN()+1) * i + _dinfo.fullN();
          _nullBeta[id] = _glmf.link(_responseMean[i]);
        }
      _nullGradient = new GLMGradientFunc(_job,_objReg,_parms,_dinfo,0,0,_bc).getGradient(_nullBeta);
      _lmax = ArrayUtils.linfnorm(_nullGradient._gradient,_dinfo.fullN()) / Math.max(1e-2, _parms._alpha[0]);
      if (_parms._lambda_min_ratio == -1) {
        _parms._lambda_min_ratio = (_nobs >> 4) > _dinfo.fullN() ? 1e-4 : 1e-2;
        if(_parms._alpha[0] == 0)
          _parms._lambda_min_ratio *= 1e-2; // smaller lambda min for ridge
      }
      if(_parms._lambda == null) {
        if(_parms._lambda_search) {
          _lambdas = new double[_parms._nlambdas];
          double dec = Math.pow(_parms._lambda_min_ratio, 1.0/(_parms._nlambdas - 1));
          _lambdas[0] = _lmax;
          double l = _lmax;
          for (int i = 1; i < _parms._nlambdas; ++i)
            _lambdas[i] = (l *= dec);
        } else {
          _lambdas = new double[]{_lmax,10 * _parms._lambda_min_ratio * _lmax};
        }
      } else {
        _lambdas = _parms._lambda.clone();
        Arrays.sort(_lambdas);
        for(int i = 0; i < _lambdas.length; ++i) {
          if (_lambdas[i] <= _lmax) {
            if(i > 0) addWarning("skipping " + i + " lambdas > lmax == " + _lmax);
            _lambdas = i < _lmax
                ?ArrayUtils.append(new double[]{_lmax},Arrays.copyOfRange(_lambdas,i,_lambdas.length))
                :Arrays.copyOfRange(_lambdas,i,_lambdas.length);
            break;
          }
        }
      }
      _solver = _parms._solver.newInstance(_parms,_dinfo);
      // clone2 so that we never modify live DKV object directly, only via update() method
      _model.clone2().delete_and_lock(_job);
    }
  }

  static final long WORK_TOTAL = 1000000;
  private int _workPerIteration;

  private transient Key[] _toRemove;

  private Key[] removeLater(Key... k) {
    _toRemove = _toRemove == null ? k : ArrayUtils.append(_toRemove, k);
    return k;
  }

  @Override
  protected GLMDriver trainModelImpl() {
    return new GLMDriver();
  }

  private DataInfo _dinfo;
  private BetaConstraint _bc;
  DataInfo dinfo(){return IcedUtils.deepCopy(_dinfo);}
  public int iter() {return _iter;}

  private boolean _hasIntercept;
  private double _alpha;
  private double _objReg;
  private double [] _lambdas;
  private int _P; // length of expanded beta vector (including intercept)

  static double sparseOffset(double[] beta, DataInfo dinfo) {
    double etaOffset = 0;
    if (dinfo._normMul != null && dinfo._normSub != null && beta != null) {
      int ns = dinfo.numStart();
      for (int i = 0; i < dinfo._nums; ++i)
        etaOffset -= beta[i + ns] * dinfo._normSub[i] * dinfo._normMul[i];
    }
    return etaOffset;
  }

  // supported families
  public enum Family {
    gaussian(Link.identity), binomial(Link.logit), quasibinomial(Link.logit),poisson(Link.log),
    gamma(Link.inverse), multinomial(Link.multinomial), tweedie(Link.tweedie);
    public final Link defaultLink;
    Family(Link link){defaultLink = link;}
  }

  public enum Link {family_default, identity, logit, log, inverse, tweedie, multinomial}

  public enum Solver {
    AUTO, IRLSM, L_BFGS, COORDINATE_DESCENT_NAIVE, COORDINATE_DESCENT;
    public GLMSolver newInstance(GLMParameters parms, DataInfo dinfo){
      // pass in parms so that each solver can decide its own default parm values (e.g. default max iter)
      switch (this){
        case IRLSM: return new GramBasedSolver(this, parms);
        case COORDINATE_DESCENT: return new GramBasedSolver(this, parms);
        case L_BFGS: return new L_BFGS_Solver();
        case COORDINATE_DESCENT_NAIVE: return new CoordinateDescentSolverNaive();
        case AUTO: {
          if(parms._lambda_search && parms._alpha[0] > 0){ // l1 lambda search
            if(parms.isWeighted() && (dinfo._adaptedFrame.numCols() > 300 || dinfo.fullN() > 7000 ) && dinfo._adaptedFrame.byteSize() < (H2O.SELF._heartbeat.get_free_mem() >> 1))
              return COORDINATE_DESCENT_NAIVE.newInstance(parms,dinfo);
            return COORDINATE_DESCENT.newInstance(parms,dinfo);
          } else return (dinfo.fullN() <= 5000 && parms._family != Family.multinomial)?IRLSM.newInstance(parms,dinfo):L_BFGS.newInstance(parms,dinfo);
        }
        default: throw H2O.unimpl();
      }
    }
  }

  /**
   * Main loop of the glm algo.
   */
  final class GLMDriver extends Driver {
    private void doCleanup() {
      try {
        _solver.cleanup(new Futures()).blockForPending();
        if (_parms._lambda_search && _parms._is_cv_model)
          Scope.untrack(removeLater((Key)_train.vec(_parms._weights_column)._key));
        if (!_cv && _model != null)
          _model.unlock(_job);
      } catch (Throwable t) {
        // nada
      }
    }

    private double testDev(double[] beta) {
      throw H2O.unimpl();
    }

    private double xvalDev(int submodelId) {
      return _isXvalMain ? _xval_test_deviances[submodelId] : Double.NaN;
    }

    private double xvalDevSD(int submodelId) {
      return _isXvalMain ? _xval_test_sd[submodelId] : Double.NaN;
    }

    int [] intercepts(){
      throw H2O.unimpl();
    }

    private double lambda(int i){return _parms._lambda[i];}
    @Override
    public void computeImpl() {
      if (!_isXvalMain) { // _xvalMain is already initialized by xval
        init(true);
        if (error_count() > 0)
          throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
        _model.clone2().delete_and_lock(_job._key);
      }
      // always start from solved null (lambda==lambda_max) state
      _currentState = new GLMState(true,-1,_nullBeta,intercepts(),_nullGradient.adjustL2Pen((1-_alpha)*lambda(0))); // initial state
      for (int i = 0; i < _parms._lambda.length; ++i) {
        computeNext(_parms._lambda[i]);
        if (_parms._early_stopping && _model._output.pickBestModelId(i) != i) // converged
          break;
      }
      scoreAndUpdateModel(_model._output.pickBestModelId());
      doCleanup();
    }

    @Override
    public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller) {
      doCleanup();
      return true;
    }
  }

  private GLMSolver _solver;
  private GLMState _currentState;
  private int _iter;
  private int _maxIter;

  private double likelihood2Deviance(double l){return 2*l;}

  private Submodel saveState(GLMState state){
    double testDev = Double.NaN;
    if(_valid != null) {
      DataInfo validDinfo = _currentState.activeData().validDinfo(_valid);
      testDev = _parms._family == Family.multinomial
          ? new GLMTask.GLMResDevTaskMultinomial(_job._key, validDinfo, _currentState.denormBeta(), _nclass).doAll(validDinfo._adaptedFrame).avgDev()
          : new GLMTask.GLMResDevTask(_job._key, validDinfo, _parms, _currentState.denormBeta()).doAll(validDinfo._adaptedFrame).avgDev();
    }
    double xvalDev = _xval_test_deviances != null?_xval_test_deviances[state._lambdaId]:Double.NaN;
    double xvalDevSD = _xval_test_sd != null?_xval_test_sd[state._lambdaId]:Double.NaN;
    return _model.setCurrentSubmodel(_currentState._beta,_currentState._activePredictors,likelihood2Deviance(state._likelihood),testDev,xvalDev,xvalDevSD,_iter);
  }
  private Submodel computeNext(double lambda) {
    GLMState newState = applyStrongRules(_currentState,lambda);
    _model.addSubmodel(lambda); // make new submodel as a copy of the previous one
    newState = checkKKTs((lambda >= _lmax)?newState:_solver.fit(newState));
    while (!newState._isSolved)
      newState = checkKKTs(_solver.fit(newState));
    _currentState = newState;
    return saveState(newState);
  }

  private long _scoringInterval = 10000;
  private long _lastScore = System.currentTimeMillis();

  private boolean shouldScore() {
    return (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval);
  }

  private boolean lambdaSearch(){return _parms._lambda.length > 2;}
  private String LogMsg(String msg) {
    return "GLM[dest=" + dest() + ", " + _currentState + "] " + msg;
  }
  private long timeSinceLastScoring() {
    return System.currentTimeMillis() - _lastScore;
  }

  private void scoreAndUpdateModel(int submodelId) {
    if(submodelId == _model._output._selected_lambda_idx)
      return; // no need to score, nothing changed
    // compute full validation on train and test
    Log.info(LogMsg("Scoring after " + timeSinceLastScoring() + "ms"));
    long t1 = System.currentTimeMillis();
    Frame train = DKV.getGet(_parms._train);
    GLMModel model = _model.clone2();
    model._output.setSubmodelIdx(submodelId);
    model.score(train).delete();
    ModelMetrics mtrain = ModelMetrics.getFromDKV(_model, train); // updated by model.scoreAndUpdateModel
    model._output._training_metrics = mtrain;
    long t2 = System.currentTimeMillis();
    Log.info(LogMsg("Training metrics computed in " + (t2 - t1) + "ms"));
    Log.info(LogMsg(mtrain.toString()));
    if (_valid != null) {
      Frame valid = DKV.getGet(_parms._valid);
      model.score(valid).delete();
      model._output._validation_metrics = ModelMetrics.getFromDKV(_model, valid); //updated by model.scoreAndUpdateModel
    }
    model._output._scoring_history = lambdaSearch() ? _model.createLambdaSearchHistoryTable() : _sc.to2dTable();
    model.generateSummary(_parms._train, _iter);
    model.update(_job._key);
    _lastScore = System.currentTimeMillis();
    long scoringTime = System.currentTimeMillis() - t1;
    _scoringInterval = Math.max(_scoringInterval, 20 * scoringTime); // at most 5% overhead for scoring
  }
  private GLMWeightsFun _glmf;

  protected class GLMState {
    private final boolean _isSolved;
    private final int _lambdaId;
    private double _likelihood;
    double likelihood(){return _likelihood;}
    private DataInfo _activeData;
    private BetaConstraint _activeBC;
    private GLMGradientInfo _ginfo;
    // active predictors according to strong rules
    //   - can be null if all active
    //   - ids can be > N for family==multinomial
    private int [] _activePredictors;
    private double [] _beta; // vector of active coefficients
    private boolean _isScored;

    GLMState(boolean isSolved, int lambdaId, double[] beta, int[] activePredictors, double likelihood){
      this._isSolved = isSolved;
      this._lambdaId = lambdaId;
      this._likelihood = likelihood;
      this._beta = beta;
      this._activePredictors = activePredictors;
    }
    GLMState(boolean isSolved, int lambdaId, double[] beta, int[] activePredictors, GLMGradientInfo ginfo){
      this(isSolved,lambdaId,beta,activePredictors,ginfo._likelihood);
      _gradient = ginfo;
    }

    double l1pen(){return _alpha*lambda();}
    double l2pen(){return (1-_alpha)*lambda();}
    private GLMGradientInfo _gradient; // optional, full gradient (no predictors filtered) with no penalty

    private boolean isFiltered(){return _activePredictors != null;}

    public double lambda(){return _lambdas[_lambdaId];}
    public double [] beta(){return _beta.clone();}
    public GLMParameters parms(){return GLM.this._parms;}

    DataInfo activeData(){
      if(_activeData == null)
        _activeData = (isFiltered()?GLM.this._dinfo.filterExpandedColumns(_activePredictors):GLM.this._dinfo);
      return _activeData;
    }

    DataInfo[] activeDataMultinomial() {
      throw H2O.unimpl();
    }

    BetaConstraint activeBC(){
      if(_activeBC == null)
        _activeBC = (isFiltered()?GLM.this._bc.filterExpandedColumns(_activePredictors):GLM.this._bc);
      return _activeBC;
    }

    GLMGradientFunc gradientFunc(){
      return new GLMGradientFunc(_job,_objReg,_parms,activeData(),_alpha,lambda(),activeBC());
    }
    public GLMGradientInfo gradient(){
      if(_ginfo == null)
        _ginfo = gradientFunc().getGradient(_beta);
      return _ginfo;
    }

    GLMWeightsFun glmWeightsFun(){return _glmf;}

    private double[] denormBeta() {
      return activeData().denormalizeBeta(_beta);
    }

    private double penalty(double [] beta){
      double lambda = lambda();
      if(lambda == 0) return 0;
      int P = activeData().fullN();
      if(_alpha == 0) return .5*lambda*ArrayUtils.l2norm2(beta,P);
      if(_alpha == 1) return lambda*ArrayUtils.l1norm(beta,P);
      return (1-_alpha)*.5*lambda*ArrayUtils.l2norm2(beta,P)
          + _alpha*lambda*ArrayUtils.l1norm(beta,P);
    }
    public double objective(double [] beta, double likelihood){
      return likelihood*_objReg + penalty(beta);
    }
    public double objective(){return objective(_beta, _likelihood);}

    public boolean update(double [] beta, double likelihood, int niter){
      if(_job.stop_requested()) throw new Job.JobCancelledException();
      _iter += niter;
      _sc.addIterationScore(_iter, likelihood, objective(beta,likelihood));
      _job.update(_workPerIteration*niter,this.toString());
      this._likelihood = likelihood;
      System.arraycopy(beta,0,this._beta,0,beta.length);
      if(shouldScore()) {
        saveState(this);
        scoreAndUpdateModel(_model._output.pickBestModelId());
        _isScored = true;
      } else _isScored = false;
      _ginfo = null;
      return !_job.stop_requested() && _iter < _maxIter;
    }
    public boolean update(double [] beta, GLMGradientInfo ginfo, int niter){
      boolean res = update(beta,ginfo._likelihood,niter);
      _ginfo = ginfo;
      return res;
    }

    String LogMsg(String s){
      throw H2O.unimpl();
    }
    int maxIterations() {return _parms._max_iterations - _iter;}

    GLMGradientInfo nullGradient() {
      throw H2O.unimpl();
    }
    double objReg() {return _objReg;}
    boolean hasIntercept() {
      return _parms._intercept;
    }

    double betaEpsilon(){return betaEpsilon(1e-4);}
    double betaEpsilon(double defaultValue) {return _parms._beta_epsilon == -1?defaultValue:_parms._beta_epsilon;}
    double objectiveEpsilon(){return objectiveEpsilon(1e-4);}
    double objectiveEpsilon(double defaultValue) {return _parms._objective_epsilon == -1?defaultValue:_parms._objective_epsilon;}
    double gradientEpsilon(){return gradientEpsilon(1e-4);}
    double gradientEpsilon(double defaultValue) {return _parms._gradient_epsilon == -1?defaultValue:_parms._gradient_epsilon;}

    public Key<Job> jobKey() {return _job._key;}

    OptimizationUtils.LineSearchSolver _ls;
    OptimizationUtils.LineSearchSolver lsSolver() {
      if(_ls == null) _ls = (_alpha > 0 || _bc.hasBounds())
          ?new OptimizationUtils.MoreThuente<GLMGradientInfo>(gradientFunc(),beta())
          :new OptimizationUtils.SimpleBacktrackingLS<GLMGradientInfo>(gradientFunc(),beta());
      return _ls;
    }
    public int nclasses() {return GLM.this.nclasses();}
    public long nobs() {return _nobs;}

    void setZValues(double[] zvalues, double se, boolean seEst, double[][] inv) {
      throw H2O.unimpl();
    }
    boolean removeCollinearColumns() {
      return _parms._remove_collinear_columns;
    }
    boolean computePValues() {return _parms._compute_p_values;}
    public int iter() {return _iter;}

    void addWarning(String s) {
      _model.addWarning(s);
      _model.clone2().update(_job);
    }

    public void removeCols(int[] collinear_cols) {
      _activeData = null;
      _activeBC = null;
      int [] activePreds = _activePredictors == null?ArrayUtils.seq(0, _beta.length):_activePredictors;
      _activePredictors = ArrayUtils.sorted_set_diff(activePreds,collinear_cols);
      throw H2O.unimpl();
    }
  }

  private GLMState applyStrongRules(GLMState state, double lambda){
    throw H2O.unimpl();
  }

  private GLMState checkKKTs(GLMState s){
    throw H2O.unimpl();
  }

  public static class GLMGradientInfo extends OptimizationUtils.GradientInfo {
    final double _likelihood;
    final double _l2pen;
    final double [] _beta;
    final int _icptStride;

    GLMGradientInfo(double[] beta, double likelihood, double objVal, double[] grad, double l2pen, int icptStride) {
      super(objVal, grad);
      _beta = beta;
      _likelihood = likelihood;
      _l2pen = l2pen;
      _icptStride = icptStride;
    }

    GLMGradientInfo adjustL2Pen(double l2pen){
      double ldiff = l2pen - _l2pen;
      double l2norm = 0;
      double [] grad = _gradient.clone();
      int icpt = _icptStride;
      for(int i = 0; i < _beta.length; ++i){
        if(i != icpt){
          double b = _beta[i];
          grad[i] += ldiff*b;
          l2norm += b*b;
        } else icpt += (_icptStride + 1);
      }
      return new GLMGradientInfo(_beta,_likelihood,_objVal + .5*ldiff*l2norm,grad,l2pen,_icptStride);
    }
    public String toString(){
      return "GLM grad info: _likelihood = " + _likelihood + super.toString();
    }
  }

}
