package hex.glm;

import hex.DataInfo;
import hex.ModelBuilder;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.glm.GLMModel.GLMOutput;
import hex.glm.GLMModel.GLMParameters;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMModel.GLMWeightsFun;
import hex.glm.GLMModel.Submodel;
import hex.glm.GLMTask.*;
import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import hex.gram.Gram.NonSPDMatrixException;
import hex.optimization.ADMM;
import hex.optimization.ADMM.L1Solver;
import hex.optimization.ADMM.ProximalSolver;
import hex.optimization.L_BFGS;
import hex.optimization.L_BFGS.ProgressMonitor;
import hex.optimization.L_BFGS.Result;
import hex.optimization.OptimizationUtils.*;
import jsr166y.CountedCompleter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.util.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Random;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 */
public class GLM extends ModelBuilder<GLMModel,GLMParameters,GLMOutput> {
  protected boolean _cv; // flag signalling this is MB for one of the fold-models during cross-validation
  static NumberFormat lambdaFormatter = new DecimalFormat(".##E0");
  static NumberFormat devFormatter = new DecimalFormat(".##");

  public static final int SCORING_INTERVAL_MSEC = 15000; // scoreAndUpdateModel every minute unless score every iteration is set
  public String _generatedWeights = null;
  public GLM(boolean startup_once){super(new GLMParameters(),startup_once);}
  public GLM(GLMModel.GLMParameters parms) {
    super(parms);
    init(false);
  }
  public GLM(GLMModel.GLMParameters parms,Key dest) {
    super(parms,dest);
    init(false);
  }

  private transient GLMDriver _driver;
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

  @Override public boolean havePojo() { return true; }
  @Override public boolean haveMojo() { return true; }

  private double _lambdaCVEstimate = Double.NaN; // lambda cross-validation estimate
  private boolean _doInit = true;  // flag setting whether or not to run init
  private double [] _xval_test_deviances;
  private double [] _xval_test_sd;

  /**
   * GLM implementation of N-fold cross-validation.
   * We need to compute the sequence of lambdas for the main model so the folds share the same lambdas.
   * We also want to set the _cv flag so that the dependent jobs know they're being run withing CV (so e.g. they do not unlock the models in the end)
   * @return Cross-validation Job
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
   *   1. compute cross-validated lambda estimate
   *   2. set the lambda estimate too all n-folds models (might require extra model fitting if the particular model stopped too early!)
   *   3. compute cross-validated scoring history (cross-validated deviance standard error per lambda)
   *   4. unlock the n-folds models (they are changed here, so the unlocking happens here)
   */
  @Override
  public void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders) {
    if(_parms._lambda_search) {
      _xval_test_deviances = new double[_parms._lambda.length];
      _xval_test_sd = new double [_parms._lambda.length];
      double bestTestDev = Double.POSITIVE_INFINITY;
      int lmin_max = 0;
      for (int i = 0; i < cvModelBuilders.length; ++i) {
        GLM g = (GLM) cvModelBuilders[i];
        lmin_max = Math.max(lmin_max,g._model._output._best_lambda_idx);
      }
      int lidx = 0;
      int bestId = 0;
      int cnt = 0;
      for (; lidx < lmin_max; ++lidx) {
        double testDev = 0;
        for (int i = 0; i < cvModelBuilders.length; ++i) {
          GLM g = (GLM) cvModelBuilders[i];
          double x = _parms._lambda[lidx];
          if (g._model._output.getSubmodel(x) == null)
            g._driver.computeSubmodel(lidx, x);
          testDev += g._model._output.getSubmodel(x).devianceTest;
        }
        double testDevAvg = testDev / cvModelBuilders.length;
        double testDevSE = 0;
        // compute deviance standard error
        for (int i = 0; i < cvModelBuilders.length; ++i) {
          GLM g = (GLM) cvModelBuilders[i];
          double x = _parms._lambda[lidx];
          if (g._model._output.getSubmodel(x) == null)
            g._driver.computeSubmodel(lidx, x); // stopped too early, need to FIT extra model
          double diff = testDevAvg - (g._model._output.getSubmodel(x).devianceTest);
          testDevSE += diff*diff;
        }
        _xval_test_sd[lidx] = Math.sqrt(testDevSE/((cvModelBuilders.length-1)*cvModelBuilders.length));
        _xval_test_deviances[lidx] = testDevAvg;
        if(testDevAvg < bestTestDev) {
          bestTestDev = testDevAvg;
          bestId = lidx;
        }
        // early stopping - no reason to move further if we're overfitting
        if(testDevAvg > bestTestDev && ++cnt == 3) {
          lmin_max = lidx;
          break;
        }
      }
      for (int i = 0; i < cvModelBuilders.length; ++i) {
        GLM g = (GLM) cvModelBuilders[i];
        if(g._toRemove != null)
          for(Key k:g._toRemove)
            Keyed.remove(k);
      }
      _parms._lambda = Arrays.copyOf(_parms._lambda,lmin_max+1);
      _xval_test_deviances = Arrays.copyOf(_xval_test_deviances, lmin_max+1);
      _xval_test_sd = Arrays.copyOf(_xval_test_sd, lmin_max+1);
      for (int i = 0; i < cvModelBuilders.length; ++i) {
        GLM g = (GLM) cvModelBuilders[i];
        g._model._output.setSubmodelIdx(bestId);
        g._model.update(_job);
      }
      double bestDev = _xval_test_deviances[bestId];
      double bestDev1se = bestDev + _xval_test_sd[bestId];
      int bestId1se = bestId;
      while(bestId1se > 0 && _xval_test_deviances[bestId1se-1] <= bestDev1se)
        --bestId1se;
      _lambdaCVEstimate = _parms._lambda[bestId];
      _model._output._lambda_1se = bestId1se;
      _model._output._best_lambda_idx = bestId;
    }
    for (int i = 0; i < cvModelBuilders.length; ++i) {
      GLM g = (GLM) cvModelBuilders[i];
      g._model.unlock(_job);
    }
    _doInit = false;
    _cv = false;
  }

  protected void checkMemoryFootPrint(DataInfo activeData) {
    if (_parms._solver == Solver.IRLSM || _parms._solver == Solver.COORDINATE_DESCENT) {
      int p = activeData.fullN();
      HeartBeat hb = H2O.SELF._heartbeat;
      long mem_usage = (long) (hb._cpus_allowed * (p * p + activeData.largestCat()) * 8/*doubles*/ * (1 + .5 * Math.log((double) _train.lastVec().nChunks()) / Math.log(2.))); //one gram per core
      long max_mem = hb.get_free_mem();
      if (mem_usage > max_mem) {
        String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
          + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
          + ") - try reducing the number of columns and/or the number of categorical factors (or switch to the L-BFGS solver).";
        error("_train", msg);
      }
    }
  }

  static class TooManyPredictorsException extends RuntimeException {}

  DataInfo _dinfo;

  private transient DataInfo _validDinfo;
  // time per iteration in ms

  private static class ScoringHistory {
    private ArrayList<Integer> _scoringIters = new ArrayList<>();
    private ArrayList<Long> _scoringTimes = new ArrayList<>();
    private ArrayList<Double> _likelihoods = new ArrayList<>();
    private ArrayList<Double> _objectives = new ArrayList<>();

    public synchronized void addIterationScore(int iter, double likelihood, double obj) {
      if (_scoringIters.size() > 0 && _scoringIters.get(_scoringIters.size() - 1) == iter)
        return; // do not record twice, happens for the last iteration, need to record scoring history in checkKKTs because of gaussian fam.
      _scoringIters.add(iter);
      _scoringTimes.add(System.currentTimeMillis());
      _likelihoods.add(likelihood);
      _objectives.add(obj);
    }

    public synchronized TwoDimTable to2dTable() {
      String[] cnames = new String[]{"timestamp", "duration", "iterations", "negative_log_likelihood", "objective"};
      String[] ctypes = new String[]{"string", "string", "int", "double", "double"};
      String[] cformats = new String[]{"%s", "%s", "%d", "%.5f", "%.5f"};
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_scoringIters.size()], cnames, ctypes, cformats, "");
      int j = 0;
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      for (int i = 0; i < _scoringIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, fmt.print(_scoringTimes.get(i)));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
        res.set(i, col++, _scoringIters.get(i));
        res.set(i, col++, _likelihoods.get(i));
        res.set(i, col++, _objectives.get(i));
      }
      return res;
    }
  }

  private static class LambdaSearchScoringHistory {
    ArrayList<Long> _scoringTimes = new ArrayList<>();
    private ArrayList<Double> _lambdas = new ArrayList<>();
    private ArrayList<Integer> _lambdaIters = new ArrayList<>();
    private ArrayList<Integer> _lambdaPredictors = new ArrayList<>();
    private ArrayList<Double> _lambdaDevTrain = new ArrayList<>();
    private ArrayList<Double> _lambdaDevTest;
    private ArrayList<Double> _lambdaDevXval;
    private ArrayList<Double> _lambdaDevXvalSE;


    public LambdaSearchScoringHistory(boolean hasTest, boolean hasXval) {
      if(hasTest || true)_lambdaDevTest = new ArrayList<>();
      if(hasXval){
        _lambdaDevXval = new ArrayList<>();
        _lambdaDevXvalSE = new ArrayList<>();
      }
    }

    public synchronized void addLambdaScore(int iter, int predictors, double lambda, double devRatioTrain, double devRatioTest, double devRatioXval, double devRatoioXvalSE) {
      _scoringTimes.add(System.currentTimeMillis());
      _lambdaIters.add(iter);
      _lambdas.add(lambda);
      _lambdaPredictors.add(predictors);
      _lambdaDevTrain.add(devRatioTrain);
      if(_lambdaDevTest != null)_lambdaDevTest.add(devRatioTest);
      if(_lambdaDevXval != null)_lambdaDevXval.add(devRatioXval);
      if(_lambdaDevXvalSE != null)_lambdaDevXvalSE.add(devRatoioXvalSE);
    }
    public synchronized TwoDimTable to2dTable() {

      String[] cnames = new String[]{"timestamp", "duration", "iteration", "lambda", "predictors", "deviance_train"};
      if(_lambdaDevTest != null)
        cnames = ArrayUtils.append(cnames,"deviance_test");
      if(_lambdaDevXval != null)
        cnames = ArrayUtils.append(cnames,new String[]{"deviance_xval","deviance_se"});
      String[] ctypes = new String[]{"string", "string", "int", "string","int", "double"};
      if(_lambdaDevTest != null)
        ctypes = ArrayUtils.append(ctypes,"double");
      if(_lambdaDevXval != null)
        ctypes = ArrayUtils.append(ctypes, new String[]{"double","double"});
      String[] cformats = new String[]{"%s", "%s", "%d","%s", "%d", "%.3f"};
      if(_lambdaDevTest != null)
        cformats = ArrayUtils.append(cformats,"%.3f");
      if(_lambdaDevXval != null)
        cformats = ArrayUtils.append(cformats,new String[]{"%.3f","%.3f"});
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_lambdaIters.size()], cnames, ctypes, cformats, "");
      int j = 0;
      DateTimeFormatter fmt = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
      for (int i = 0; i < _lambdaIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, fmt.print(_scoringTimes.get(i)));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
        res.set(i, col++, _lambdaIters.get(i));
        res.set(i, col++, lambdaFormatter.format(_lambdas.get(i)));
        res.set(i, col++, _lambdaPredictors.get(i));
        res.set(i, col++, _lambdaDevTrain.get(i));
        if(_lambdaDevTest != null && _lambdaDevTest.size() > i)
          res.set(i, col++, _lambdaDevTest.get(i));
        if(_lambdaDevXval != null && _lambdaDevXval.size() > i) {
          res.set(i, col++, _lambdaDevXval.get(i));
          res.set(i, col++, _lambdaDevXvalSE.get(i));
        }
      }
      return res;
    }
  }

  private transient ScoringHistory _sc;
  private transient LambdaSearchScoringHistory _lsc;

  long _t0 = System.currentTimeMillis();

  private transient double _iceptAdjust = 0;

  private double _lmax;
  private transient long _nobs;
  private transient GLMModel _model;

  @Override
  public int nclasses() {
    if (_parms._family == Family.multinomial || _parms._family == Family.ordinal)
      return _nclass;
    if (_parms._family == Family.binomial || _parms._family == Family.quasibinomial)
      return 2;
    return 1;
  }
  private transient double[] _nullBeta;

  private double[] getNullBeta() {
    if (_nullBeta == null) {
      if (_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
        _nullBeta = MemoryManager.malloc8d((_dinfo.fullN() + 1) * nclasses());
        int N = _dinfo.fullN() + 1;
        if(_parms._intercept)
          if (_parms._family == Family.ordinal) { // ordinal regression use random sorted start values
            Random rng = RandomUtils.getRNG(_parms._seed);
            int lastClass = nclasses()-1;
            double[] tempIcpt = new double[lastClass];
            for (int i = 0; i < lastClass; i++) {  // only contains nclass-2 thresholds here
              tempIcpt[i] = (-1+2*rng.nextDouble()) * nclasses(); // generate threshold from -nclasses to +nclasses
            }
            Arrays.sort(tempIcpt);

            for (int i = 0; i < lastClass; i++)
              _nullBeta[_dinfo.fullN() + i * N] = tempIcpt[i];
          } else {
          for (int i = 0; i < nclasses(); ++i)
            _nullBeta[_dinfo.fullN() + i * N] = Math.log(_state._ymu[i]);
        }
      } else {
        _nullBeta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        if (_parms._intercept && !(_parms._family == Family.quasibinomial))
          _nullBeta[_dinfo.fullN()] = new GLMModel.GLMWeightsFun(_parms).link(_state._ymu[0]);
        else
          _nullBeta[_dinfo.fullN()] = 0;
      }
    }
    return _nullBeta;
  }

  protected boolean computePriorClassDistribution(){return (_parms._family==Family.multinomial)||(_parms._family==Family.ordinal);}

  @Override
  public void init(boolean expensive) {
    super.init(expensive);
    hide("_balance_classes", "Not applicable since class balancing is not required for GLM.");
    hide("_max_after_balance_size", "Not applicable since class balancing is not required for GLM.");
    hide("_class_sampling_factors", "Not applicable since class balancing is not required for GLM.");
    _parms.validate(this);
    if(_response != null) {
      if(!isClassifier() && _response.isCategorical())
        error("_response", H2O.technote(2, "Regression requires numeric response, got categorical."));
      if ((_parms._solver.equals(Solver.GRADIENT_DESCENT_LH) || _parms._solver.equals(Solver.GRADIENT_DESCENT_SQERR)) && !_parms._family.equals(Family.ordinal))
        error("_solver", "Solvers GRADIENT_DESCENT_LH and GRADIENT_DESCENT_SQERR are only " +
                "supported for ordinal regression.  Do not choose them unless you specify your family to be ordinal");
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
          if (_response.min() <= 0) error("_family", "Response value for gamma distribution must be greater than 0.");
          break;
        case tweedie:
          if (_nclass != 1) error("_family", H2O.technote(2, "Tweedie requires the response to be numeric."));
          break;
        case quasibinomial:
          if (_nclass != 1) error("_family", H2O.technote(2, "Quasi_binomial requires the response to be numeric."));
          break;
        case ordinal:
          if (_nclass <= 2)
            error("_family", H2O.technote(2, "Ordinal requires a categorical response with at least 3 levels (for 2 class problem use family=binomial."));
          if (_parms._link == Link.oprobit || _parms._link == Link.ologlog)
            error("_link", "Ordinal regression only supports ologit as link.");
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
      if (_parms._alpha == null)
        _parms._alpha = new double[]{_parms._solver == Solver.L_BFGS ? 0 : .5};
      if (_parms._lambda_search  &&_parms._nlambdas == -1)
          _parms._nlambdas = _parms._alpha[0] == 0?30:100; // fewer lambdas needed for ridge
      _lsc = new LambdaSearchScoringHistory(_parms._valid != null,_parms._nfolds > 1);
      _sc = new ScoringHistory();
      _train.bulkRollups(); // make sure we have all the rollups computed in parallel
      _sc = new ScoringHistory();
      _t0 = System.currentTimeMillis();
      if (_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0)
        _parms._use_all_factor_levels = true;
      if (_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
      _dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, _parms._missing_values_handling == MissingValuesHandling.Skip, _parms._missing_values_handling == MissingValuesHandling.MeanImputation, false, hasWeightCol(), hasOffsetCol(), hasFoldCol(), _parms.interactionSpec());

      if (_parms._max_iterations == -1) { // fill in default max iterations
        int numclasses = (_parms._family == Family.multinomial)||(_parms._family == Family.ordinal)?nclasses():1;
        if (_parms._solver == Solver.L_BFGS) {
          _parms._max_iterations = _parms._lambda_search ? _parms._nlambdas * 100 * numclasses : numclasses * Math.max(20, _dinfo.fullN() >> 2);
          if(_parms._alpha[0] > 0)
            _parms._max_iterations *= 10;
        } else
          _parms._max_iterations = _parms._lambda_search ? 10 * _parms._nlambdas : 50;
      }
      if (_valid != null)
        _validDinfo = _dinfo.validDinfo(_valid);
      _state = new ComputationState(_job, _parms, _dinfo, null, nclasses());
      // skipping extra rows? (outside of weights == 0)GLMT
      boolean skippingRows = (_parms._missing_values_handling == MissingValuesHandling.Skip && _train.hasNAs());
      if (hasWeightCol() || skippingRows) { // need to re-compute means and sd
        boolean setWeights = skippingRows;// && _parms._lambda_search && _parms._alpha[0] > 0;
        if (setWeights) {
          Vec wc = _weights == null ? _dinfo._adaptedFrame.anyVec().makeCon(1) : _weights.makeCopy();
          _dinfo.setWeights(_generatedWeights = "__glm_gen_weights", wc);
        }

        YMUTask ymt = new YMUTask(_dinfo, (_parms._family == Family.multinomial)||(_parms._family == Family.ordinal)?nclasses():1, setWeights, skippingRows,true,false).doAll(_dinfo._adaptedFrame);
        if (ymt.wsum() == 0)
          throw new IllegalArgumentException("No rows left in the dataset after filtering out rows with missing values. Ignore columns with many NAs or impute your missing values prior to calling glm.");
        Log.info(LogMsg("using " + ymt.nobs() + " nobs out of " + _dinfo._adaptedFrame.numRows() + " total"));
        // if sparse data, need second pass to compute variance
        _nobs = ymt.nobs();
        if (_parms._obj_reg == -1)
          _parms._obj_reg = 1.0 / ymt.wsum();
        if(!_parms._stdOverride)
          _dinfo.updateWeightedSigmaAndMean(ymt.predictorSDs(), ymt.predictorMeans());
        if (_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
          _state._ymu = MemoryManager.malloc8d(_nclass);
          for (int i = 0; i < _state._ymu.length; ++i)
            _state._ymu[i] = _priorClassDist[i];//ymt.responseMeans()[i];
        } else
        _state._ymu = _parms._intercept?ymt._yMu:new double[]{_parms.linkInv(0)};
      } else {
        _nobs = _train.numRows();
        if (_parms._obj_reg == -1)
          _parms._obj_reg = 1.0 / _nobs;
        if (_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
          _state._ymu = MemoryManager.malloc8d(_nclass);
          for (int i = 0; i < _state._ymu.length; ++i)
            _state._ymu[i] = _priorClassDist[i];
        } else
          _state._ymu = new double[]{_parms._intercept?_train.lastVec().mean():_parms.linkInv(0)};
      }
      BetaConstraint bc = (_parms._beta_constraints != null)?new BetaConstraint(_parms._beta_constraints.get()):new BetaConstraint();
      if((bc.hasBounds() || bc.hasProximalPenalty()) && _parms._compute_p_values)
        error("_compute_p_values","P-values can not be computed for constrained problems");
      if(bc.hasBounds())
        _parms._early_stopping = false; // PUBDEV-4641: early stopping does not work correctly with non-negative option
      _state.setBC(bc);
      if(hasOffsetCol() && _parms._intercept) { // fit intercept
        GLMGradientSolver gslvr = new GLMGradientSolver(_job,_parms, _dinfo.filterExpandedColumns(new int[0]), 0, _state.activeBC());
        double [] x = new L_BFGS().solve(gslvr,new double[]{-_offset.mean()}).coefs;
        Log.info(LogMsg("fitted intercept = " + x[0]));
        x[0] = _parms.linkInv(x[0]);
        _state._ymu = x;
      }
      if (_parms._prior > 0)
        _iceptAdjust = -Math.log(_state._ymu[0] * (1 - _parms._prior) / (_parms._prior * (1 - _state._ymu[0])));
      ArrayList<Vec> vecs = new ArrayList<>();
      if(_weights != null) vecs.add(_weights);
      if(_offset != null) vecs.add(_offset);
      vecs.add(_response);
      double [] beta = getNullBeta();
      GLMGradientInfo ginfo = new GLMGradientSolver(_job,_parms, _dinfo, 0, _state.activeBC()).getGradient(beta);
      _lmax = lmax(ginfo._gradient);
      _state.setLambdaMax(_lmax);
      _model = new GLMModel(_result, _parms, GLM.this, _state._ymu, _dinfo._adaptedFrame.lastVec().sigma(), _lmax, _nobs);
      if (_parms._lambda_min_ratio == -1) {
        _parms._lambda_min_ratio = (_nobs >> 4) > _dinfo.fullN() ? 1e-4 : 1e-2;
        if(_parms._alpha[0] == 0)
          _parms._lambda_min_ratio *= 1e-2; // smalelr lambda min for ridge as we are starting quite high
      }

      _state.updateState(beta,ginfo);
      if (_parms._lambda == null) {  // no lambda given, we will base lambda as a fraction of lambda max
        if (_parms._lambda_search) {
          _parms._lambda = new double[_parms._nlambdas];
          double dec = Math.pow(_parms._lambda_min_ratio, 1.0/(_parms._nlambdas - 1));
          _parms._lambda[0] = _lmax;
          double l = _lmax;
          for (int i = 1; i < _parms._nlambdas; ++i)
            _parms._lambda[i] = (l *= dec);
          // todo set the null submodel
        } else
          _parms._lambda = new double[]{10 * _parms._lambda_min_ratio * _lmax};
      }
      if(!Double.isNaN(_lambdaCVEstimate)){
        for(int i = 0; i < _parms._lambda.length; ++i)
          if(_parms._lambda[i] < _lambdaCVEstimate){
            _parms._lambda = Arrays.copyOf(_parms._lambda,i+1);
            break;
          }
        _parms._lambda[_parms._lambda.length-1] = _lambdaCVEstimate;
      }
      if(_parms._objective_epsilon == -1) {
        if(_parms._lambda_search)
          _parms._objective_epsilon = 1e-4;
        else // lower default objective epsilon for non-standardized problems (mostly to match classical tools)
          _parms._objective_epsilon =  _parms._lambda[0] == 0?1e-6:1e-4;
      }
      if(_parms._gradient_epsilon == -1) {
        _parms._gradient_epsilon = _parms._lambda[0] == 0 ? 1e-6 : 1e-4;
        if(_parms._lambda_search) _parms._gradient_epsilon *= 1e-2;
      }
      // clone2 so that I don't change instance which is in the DKV directly
      // (clone2 also shallow clones _output)
      _model.clone2().delete_and_lock(_job._key);
    }
  }

  protected static final long WORK_TOTAL = 1000000;

  transient Key [] _toRemove;

  private Key[] removeLater(Key ...k){
    _toRemove = _toRemove == null?k:ArrayUtils.append(_toRemove,k);
    return k;
  }

  @Override protected GLMDriver trainModelImpl() { return _driver = new GLMDriver(); }

  private final double lmax(double[] grad) {
    return Math.max(ArrayUtils.maxValue(grad), -ArrayUtils.minValue(grad)) / Math.max(1e-2, _parms._alpha[0]);
  }
  private transient ComputationState _state;

  /**
   * Main loop of the glm algo.
   */
  public final class GLMDriver extends Driver implements ProgressMonitor {
    private long _workPerIteration;
    private transient double[][] _vcov;


    private void doCleanup() {
      try {
        if(_parms._lambda_search && _parms._is_cv_model)
          Scope.untrack(removeLater(_dinfo.getWeightsVec()._key));
        if(!_cv && _model!=null)
          _model.unlock(_job);
      } catch(Throwable t){
        // nada
      }
    }
    private transient Cholesky _chol;
    private transient L1Solver _lslvr;


    int [] findZeros(double [] vals){
      int [] res = new int[4];
      int cnt = 0;
      for(int i = 0; i < vals.length; ++i){
        if(vals[i] == 0){
          if(res.length == cnt)
            res = Arrays.copyOf(res,res.length*2);
          res[cnt++] = i;
        }
      }
      return Arrays.copyOf(res,cnt);
    }
    private double[] ADMM_solve(Gram gram, double [] xy) {
      if(_parms._remove_collinear_columns || _parms._compute_p_values) {
        if(!_parms._intercept) throw H2O.unimpl();
        ArrayList<Integer> ignoredCols = new ArrayList<>();
        Cholesky chol = ((_state._iter == 0)?gram.qrCholesky(ignoredCols, _parms._standardize):gram.cholesky(null));
        if(!ignoredCols.isEmpty() && !_parms._remove_collinear_columns) {
          int [] collinear_cols = new int[ignoredCols.size()];
          for(int i = 0; i < collinear_cols.length; ++i)
            collinear_cols[i] = ignoredCols.get(i);
          throw new Gram.CollinearColumnsException("Found collinear columns in the dataset. P-values can not be computed with collinear columns in the dataset. Set remove_collinear_columns flag to true to remove collinear columns automatically. Found collinear columns " + Arrays.toString(ArrayUtils.select(_dinfo.coefNames(),collinear_cols)));
        }
        if(!chol.isSPD()) throw new NonSPDMatrixException();
        _chol = chol;
        if(!ignoredCols.isEmpty()) { // got some redundant cols
          int [] collinear_cols = new int[ignoredCols.size()];
          for(int i = 0; i < collinear_cols.length; ++i)
            collinear_cols[i] = ignoredCols.get(i);
          String [] collinear_col_names = ArrayUtils.select(_state.activeData().coefNames(),collinear_cols);
          // need to drop the cols from everywhere
          _model.addWarning("Removed collinear columns " + Arrays.toString(collinear_col_names));
          Log.warn("Removed collinear columns " + Arrays.toString(collinear_col_names));
          _state.removeCols(collinear_cols);
          gram.dropCols(collinear_cols);
          xy = ArrayUtils.removeIds(xy,collinear_cols);
        }
        xy = xy.clone();
        chol.solve(xy);
      } else {
        gram = gram.deep_clone();
        xy = xy.clone();
        GramSolver slvr = new GramSolver(gram.clone(), xy.clone(), _parms._intercept, _state.l2pen(),_state.l1pen(), _state.activeBC()._betaGiven, _state.activeBC()._rho, _state.activeBC()._betaLB, _state.activeBC()._betaUB);
        _chol = slvr._chol;
        if(_state.l1pen() == 0 && !_state.activeBC().hasBounds()) {
          slvr.solve(xy);
        } else {
          xy = MemoryManager.malloc8d(xy.length);
          if(_state._u == null && (_parms._family != Family.multinomial)) _state._u = MemoryManager.malloc8d(_state.activeData().fullN()+1);
            (_lslvr = new ADMM.L1Solver(1e-4, 10000, _state._u)).solve(slvr, xy, _state.l1pen(), _parms._intercept, _state.activeBC()._betaLB, _state.activeBC()._betaUB);
        }
      }
      return xy;
    }

    private void fitCOD_multinomial(Solver s) {
      double[] beta = _state.betaMultinomial();
      LineSearchSolver ls;

      do {
        beta = beta.clone();
           for (int c = 0; c < _nclass; ++c) {
            _state.setActiveClass(c);
            boolean onlyIcpt = _state.activeDataMultinomial(c).fullN() == 0;

            if (_state.l1pen()==0) {
              if (_state.ginfoNull())
                _state.updateState(beta, _state.gslvr().getGradient(beta));
              ls = new MoreThuente(_state.gslvrMultinomial(c), _state.betaMultinomial(c, beta), _state.ginfoMultinomial(c));
            } else
              ls = new SimpleBacktrackingLS(_state.gslvrMultinomial(c), _state.betaMultinomial(c, beta), _state.l1pen());
/*            LineSearchSolver ls = (_state.l1pen() == 0)
                    ? new MoreThuente(_state.gslvrMultinomial(c), _state.betaMultinomial(c, beta), _state.ginfoMultinomial(c))
                    : new SimpleBacktrackingLS(_state.gslvrMultinomial(c), _state.betaMultinomial(c, beta), _state.l1pen());*/

            new GLMMultinomialUpdate(_state.activeDataMultinomial(), _job._key, beta, c).doAll(_state.activeDataMultinomial()._adaptedFrame);
            ComputationState.GramXY gram = _state.computeGram(_state.betaMultinomial(c, beta), s);
            double[] betaCnd = COD_solve(gram, _state._alpha, _state.lambda());

            if (!onlyIcpt && !ls.evaluate(ArrayUtils.subtract(betaCnd, ls.getX(), betaCnd))) {
              Log.info(LogMsg("Ls failed " + ls));
              continue;
            }
            _state.setBetaMultinomial(c, beta, ls.getX());  // set new beta
          }

        _state.setActiveClass(-1); // only reset after going through a whole set of classes.  Not sure about this
      } while (progress(beta, _state.gslvr().getMultinomialLikelihood(beta))); // only need likelihood inside loop
      if (_parms._lambda_search ) {
        _state.updateState(beta, _state.gslvr().getGradient(beta));  // only calculate _gradient here when needed
      }
    }

    private void fitIRLSM_multinomial(Solver s) {
      assert _dinfo._responses == 3 : "IRLSM for multinomial needs extra information encoded in additional reponses, expected 3 response vecs, got " + _dinfo._responses;

      if (s.equals(Solver.COORDINATE_DESCENT)) {
        fitCOD_multinomial(s);
      } else {
        double[] beta = _state.betaMultinomial();
        do {
          beta = beta.clone();
          for (int c = 0; c < _nclass; ++c) {
            boolean onlyIcpt = _state.activeDataMultinomial(c).fullN() == 0;
            _state.setActiveClass(c);
            LineSearchSolver ls = (_state.l1pen() == 0)
                    ? new MoreThuente(_state.gslvrMultinomial(c), _state.betaMultinomial(c, beta), _state.ginfoMultinomial(c))
                    : new SimpleBacktrackingLS(_state.gslvrMultinomial(c), _state.betaMultinomial(c, beta), _state.l1pen());

            long t1 = System.currentTimeMillis();
            new GLMMultinomialUpdate(_state.activeDataMultinomial(), _job._key, beta, c).doAll(_state.activeDataMultinomial()._adaptedFrame);
            long t2 = System.currentTimeMillis();
            ComputationState.GramXY gram = _state.computeGram(ls.getX(), s);
            long t3 = System.currentTimeMillis();
            double[] betaCnd = ADMM_solve(gram.gram, gram.xy);

            long t4 = System.currentTimeMillis();
            if (!onlyIcpt && !ls.evaluate(ArrayUtils.subtract(betaCnd, ls.getX(), betaCnd))) {
              Log.info(LogMsg("Ls failed " + ls));
              continue;
            }
            long t5 = System.currentTimeMillis();
            _state.setBetaMultinomial(c, beta, ls.getX());
            // update multinomial
            Log.info(LogMsg("computed in " + (t2 - t1) + "+" + (t3 - t2) + "+" + (t4 - t3) + "+" + (t5 - t4) + "=" + (t5 - t1) + "ms, step = " + ls.step() + ((_lslvr != null) ? ", l1solver " + _lslvr : "")));
          }
          _state.setActiveClass(-1);
        } while (progress(beta, _state.gslvr().getGradient(beta)));
      }
    }

    // use regular gradient descend here.  Need to figure out how to adjust for the alpha, lambda for the elastic net
    private void fitIRLSM_ordinal_default(Solver s) {
      assert _dinfo._responses == 3 : "Solver for ordinal needs extra information encoded in additional reponses, " +
              "expected 3 response vecs, got " + _dinfo._responses;
      double[] beta = _state.betaMultinomial();
      int predSize = _dinfo.fullN();
      int predSizeP1 = predSize+1;
      int numClass = _state._nclasses;
      int numIcpt = numClass-1;
      double[] betaCnd = new double[predSize];  // number of predictors
      _state.gslvr().getGradient(beta); // get new gradient info with correct l2pen value.
      double l1pen =  _state.lambda() * _state._alpha;  // l2pen already calculated in gradient
      boolean stopNow = false;

      do {
        beta = beta.clone();    // copy over the coefficients
        // perform updates only on the betas excluding the intercept
        double[] grads = _state.ginfo()._gradient;

        for (int pindex=0; pindex<numIcpt; pindex++) {  // check and then update the intercepts
          int icptindex = (pindex+1)*predSizeP1-1;
          beta[icptindex] -= grads[icptindex];
          if (pindex > 0) {
            int previousIcpt = pindex*predSizeP1-1;
            if (beta[icptindex] < beta[previousIcpt]) {
              warn("Ordinal regression training: ", " intercepts of previous class exceed that " +
                      "of current class.  Make sure your training parameters are set properly.  Training will " +
                      "stop now with the last eligible parameters.");
              stopNow = true;
              for (int index = 0; index <= pindex; index++) { // restore threshold value to old ones
                icptindex = (index+1)*predSizeP1-1;
                beta[icptindex] += grads[icptindex];
              }
              break;
            }
          }
        }

        if (stopNow)  // break out of while loop
          break;

        // update all parameters with new gradient;
        for (int pindex=0; pindex<predSize; pindex++) { // add l1pen is necessary and coefficient updates
          betaCnd[pindex]=grads[pindex];
          if (l1pen>0) {
            betaCnd[pindex] += beta[pindex]>0?l1pen:(beta[pindex]==0?0:-l1pen);
          }
          beta[pindex] -= betaCnd[pindex]; // take the negative of the gradient and stuff
        }

        for (int indC = 1; indC < numIcpt; indC++) {
          int indOffset = indC * predSizeP1;
          for (int index=0; index < predSize; index++) {  // copy beta to all classes
            beta[indOffset + index] = beta[index];
          }
        }

        _state.setActiveClass(-1);
      } while (progress(beta, _state.gslvr().getGradient(beta)));
    }

    private void fitLSM(Solver s){
      long t0 = System.currentTimeMillis();
      ComputationState.GramXY gramXY = _state.computeGram(_state.beta(),s);
      Log.info(LogMsg("Gram computed in " + (System.currentTimeMillis()-t0) + "ms"));
      double [] beta = _parms._solver == Solver.COORDINATE_DESCENT?COD_solve(gramXY,_state._alpha,_state.lambda()):ADMM_solve(gramXY.gram,gramXY.xy);
      // compute mse
      double [] x = ArrayUtils.mmul(gramXY.gram.getXX(),beta);
      for(int i = 0; i < x.length; ++i)
        x[i] = (x[i] - 2*gramXY.xy[i]);
      double l = .5*(ArrayUtils.innerProduct(x,beta)/_parms._obj_reg + gramXY.yy );
      _state._iter++;
      _state.updateState(beta, l);
    }

    private void fitIRLSM(Solver s) {
      GLMWeightsFun glmw = new GLMWeightsFun(_parms);
      double [] betaCnd = _state.beta();
      LineSearchSolver ls = null;
      boolean firstIter = true;
      int iterCnt = 0;
      try {
        while (true) {
          iterCnt++;
          long t1 = System.currentTimeMillis();
          ComputationState.GramXY gram = _state.computeGram(betaCnd,s);
          long t2 = System.currentTimeMillis();
          if (!_state._lsNeeded && (Double.isNaN(gram.likelihood) || _state.objective(gram.beta, gram.likelihood) > _state.objective() + _parms._objective_epsilon)) {
            _state._lsNeeded = true;
          } else {
            if (!firstIter && !_state._lsNeeded && !progress(gram.beta, gram.likelihood)) {
              System.out.println("DONE after " + (iterCnt-1) + " iterations (1)");
              return;
            }
            betaCnd = s == Solver.COORDINATE_DESCENT?COD_solve(gram,_state._alpha,_state.lambda()):ADMM_solve(gram.gram,gram.xy);
          }
          firstIter = false;
          long t3 = System.currentTimeMillis();
          if(_state._lsNeeded) {
            if(ls == null)
              ls = (_state.l1pen() == 0 && !_state.activeBC().hasBounds())
                 ? new MoreThuente(_state.gslvr(),_state.beta(), _state.ginfo())
                 : new SimpleBacktrackingLS(_state.gslvr(),_state.beta().clone(), _state.l1pen(), _state.ginfo());
            if (!ls.evaluate(ArrayUtils.subtract(betaCnd, ls.getX(), betaCnd))) {
              Log.info(LogMsg("Ls failed " + ls));
              return;
            }
            betaCnd = ls.getX();
            if(!progress(betaCnd,ls.ginfo()))
              return;
            long t4 = System.currentTimeMillis();
            Log.info(LogMsg("computed in " + (t2 - t1) + "+" + (t3 - t2) + "+" + (t4 - t3) + "=" + (t4 - t1) + "ms, step = " + ls.step() + ((_lslvr != null) ? ", l1solver " + _lslvr : "")));
          } else
            Log.info(LogMsg("computed in " + (t2 - t1) + "+" + (t3 - t2) + "=" + (t3 - t1) + "ms, step = " + 1 + ((_lslvr != null) ? ", l1solver " + _lslvr : "")));
        }
      } catch(NonSPDMatrixException e) {
        Log.warn(LogMsg("Got Non SPD matrix, stopped."));
      }
    }

    private void fitLBFGS() {
      double [] beta = _state.beta();
      final double l1pen = _state.l1pen();
      GLMGradientSolver gslvr = _state.gslvr();
      GLMWeightsFun glmw = new GLMWeightsFun(_parms);
      if (beta == null && (_parms._family == Family.multinomial || _parms._family == Family.ordinal)) {
        beta = MemoryManager.malloc8d((_state.activeData().fullN() + 1) * _nclass);
        int P = _state.activeData().fullN() + 1;
        if(_parms._intercept)
          for (int i = 0; i < _nclass; ++i)
            beta[i * P + P - 1] = glmw.link(_state._ymu[i]);
      }
      if (beta == null) {
        beta = MemoryManager.malloc8d(_state.activeData().fullN() + 1);
        if (_parms._intercept)
          beta[beta.length - 1] = glmw.link(_state._ymu[0]);
      }
      L_BFGS lbfgs = new L_BFGS().setObjEps(_parms._objective_epsilon).setGradEps(_parms._gradient_epsilon).setMaxIter(_parms._max_iterations);
      assert beta.length == _state.ginfo()._gradient.length;
      int P = _dinfo.fullN();
      if (l1pen > 0 || _state.activeBC().hasBounds()) {
        double[] nullBeta = MemoryManager.malloc8d(beta.length); // compute ginfo at null beta to get estimate for rho
        if (_dinfo._intercept) {
          if (_parms._family == Family.multinomial) {
            for (int c = 0; c < _nclass; c++)
              nullBeta[(c + 1) * (P + 1) - 1] = glmw.link(_state._ymu[c]);
          } else
            nullBeta[nullBeta.length - 1] = glmw.link(_state._ymu[0]);
        }
        GradientInfo ginfo = gslvr.getGradient(nullBeta);
        double[] direction = ArrayUtils.mult(ginfo._gradient.clone(), -1);
        double t = 1;
        if (l1pen > 0) {
          MoreThuente mt = new MoreThuente(gslvr,nullBeta);
          mt.evaluate(direction);
          t = mt.step();
        }
        double[] rho = MemoryManager.malloc8d(beta.length);
        double r = _state.activeBC().hasBounds()?1:.1;
        BetaConstraint bc = _state.activeBC();
        // compute rhos
        for (int i = 0; i < rho.length - 1; ++i)
          rho[i] = r * ADMM.L1Solver.estimateRho(nullBeta[i] + t * direction[i], l1pen, bc._betaLB == null ? Double.NEGATIVE_INFINITY : bc._betaLB[i], bc._betaUB == null ? Double.POSITIVE_INFINITY : bc._betaUB[i]);
        for (int ii = P; ii < rho.length; ii += P + 1)
          rho[ii] = r * ADMM.L1Solver.estimateRho(nullBeta[ii] + t * direction[ii], 0, bc._betaLB == null ? Double.NEGATIVE_INFINITY : bc._betaLB[ii], bc._betaUB == null ? Double.POSITIVE_INFINITY : bc._betaUB[ii]);
        final double[] objvals = new double[2];
        objvals[1] = Double.POSITIVE_INFINITY;
        double reltol = L1Solver.DEFAULT_RELTOL;
        double abstol = L1Solver.DEFAULT_ABSTOL;
        double ADMM_gradEps = 1e-3;
        ProximalGradientSolver innerSolver = new ProximalGradientSolver(gslvr, beta, rho, _parms._objective_epsilon * 1e-1, _parms._gradient_epsilon, _state.ginfo(), this);
//        new ProgressMonitor() {
//          @Override
//          public boolean progress(double[] betaDiff, GradientInfo ginfo) {
//            return ++_state._iter < _parms._max_iterations;
//          }
//        });
        ADMM.L1Solver l1Solver = new ADMM.L1Solver(ADMM_gradEps, 250, reltol, abstol, _state._u);
        l1Solver._pm = this;
        l1Solver.solve(innerSolver, beta, l1pen, true, _state.activeBC()._betaLB, _state.activeBC()._betaUB);
        _state._u = l1Solver._u;
        _state.updateState(beta,gslvr.getGradient(beta));
      } else {
        if(!_parms._lambda_search && _state._iter == 0)
          updateProgress(false);
        Result r = lbfgs.solve(gslvr, beta, _state.ginfo(), new ProgressMonitor() {
          @Override
          public boolean progress(double[] beta, GradientInfo ginfo) {
            if(_state._iter < 4 || ((_state._iter & 3) == 0))
              Log.info(LogMsg("LBFGS, gradient norm = " + ArrayUtils.linfnorm(ginfo._gradient, false)));
            return GLMDriver.this.progress(beta,ginfo);
          }
        });
        Log.info(LogMsg(r.toString()));
        _state.updateState(r.coefs,(GLMGradientInfo)r.ginfo);
      }
    }

    private void fitCOD() {
      double [] beta = _state.beta();
      int p = _state.activeData().fullN()+ 1;
      double wsum,wsumu; // intercept denum
      double [] denums;
      boolean skipFirstLevel = !_state.activeData()._useAllFactorLevels;
      double [] betaold = beta.clone();
      double objold = _state.objective();
      int iter2=0; // total cd iters
      // get reweighted least squares vectors
      Vec[] newVecs = _state.activeData()._adaptedFrame.anyVec().makeZeros(3);
      Vec w = newVecs[0]; // fixed before each CD loop
      Vec z = newVecs[1]; // fixed before each CD loop
      Vec zTilda = newVecs[2]; // will be updated at every variable within CD loop
      long startTimeTotalNaive = System.currentTimeMillis();

      // generate new IRLS iteration
      while (iter2++ < 30) {

        Frame fr = new Frame(_state.activeData()._adaptedFrame);
        fr.add("w", w); // fr has all data
        fr.add("z", z);
        fr.add("zTilda", zTilda);

        GLMGenerateWeightsTask gt = new GLMGenerateWeightsTask(_job._key, _state.activeData(), _parms, beta).doAll(fr);
        double objVal = objVal(gt._likelihood, gt._betaw, _state.lambda());
        denums = gt.denums;
        wsum = gt.wsum;
        wsumu = gt.wsumu;
        int iter1 = 0;

        // coordinate descent loop
        while (iter1++ < 100) {
          Frame fr2 = new Frame();
          fr2.add("w", w);
          fr2.add("z", z);
          fr2.add("zTilda", zTilda); // original x%*%beta if first iteration

          for(int i=0; i < _state.activeData()._cats; i++) {
            Frame fr3 = new Frame(fr2);
            int level_num = _state.activeData()._catOffsets[i+1]-_state.activeData()._catOffsets[i];
            int prev_level_num = 0;
            fr3.add("xj", _state.activeData()._adaptedFrame.vec(i));

            boolean intercept = (i == 0); // prev var is intercept
            if(!intercept) {
              prev_level_num = _state.activeData()._catOffsets[i]-_state.activeData()._catOffsets[i-1];
              fr3.add("xjm1", _state.activeData()._adaptedFrame.vec(i-1)); // add previous categorical variable
            }
            int start_old = _state.activeData()._catOffsets[i];
            GLMCoordinateDescentTaskSeqNaive stupdate;
            if(intercept)
              stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, 4 , Arrays.copyOfRange(betaold, start_old, start_old+level_num),
                new double [] {beta[p-1]}, _state.activeData()._catLvls[i], null, null, null, null, null, skipFirstLevel).doAll(fr3);
            else
              stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, 1 , Arrays.copyOfRange(betaold, start_old,start_old+level_num),
                Arrays.copyOfRange(beta, _state.activeData()._catOffsets[i-1], _state.activeData()._catOffsets[i]) ,  _state.activeData()._catLvls[i] ,
                _state.activeData()._catLvls[i-1], null, null, null, null, skipFirstLevel ).doAll(fr3);

            for(int j=0; j < level_num; ++j)
              beta[_state.activeData()._catOffsets[i]+j] = ADMM.shrinkage(stupdate._temp[j] / wsumu, _state.lambda() * _parms._alpha[0])
                / (denums[_state.activeData()._catOffsets[i]+j] / wsumu + _state.lambda() * (1 - _parms._alpha[0]));
          }

          int cat_num = 2; // if intercept, or not intercept but not first numeric, then both are numeric .
          for (int i = 0; i < _state.activeData()._nums; ++i) {
            GLMCoordinateDescentTaskSeqNaive stupdate;
            Frame fr3 = new Frame(fr2);
            fr3.add("xj", _state.activeData()._adaptedFrame.vec(i+_state.activeData()._cats)); // add current variable col
            boolean intercept = (i == 0 && _state.activeData().numStart() == 0); // if true then all numeric case and doing beta_1

            double [] meannew=null, meanold=null, varnew=null, varold=null;
            if(i > 0 || intercept) {// previous var is a numeric var
              cat_num = 3;
              if(!intercept)
                fr3.add("xjm1", _state.activeData()._adaptedFrame.vec(i - 1 + _state.activeData()._cats)); // add previous one if not doing a beta_1 update, ow just pass it the intercept term
              if( _state.activeData()._normMul!=null ) {
                varold = new double[]{_state.activeData()._normMul[i]};
                meanold = new double[]{_state.activeData()._normSub[i]};
                if (i!= 0){
                  varnew = new double []{ _state.activeData()._normMul[i-1]};
                  meannew = new double [] { _state.activeData()._normSub[i-1]};
                }
              }
              stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, cat_num , new double [] { betaold[_state.activeData().numStart()+ i]},
                new double []{ beta[ (_state.activeData().numStart()+i-1+p)%p ]}, null, null,
                varold, meanold, varnew, meannew, skipFirstLevel ).doAll(fr3);

              beta[i+_state.activeData().numStart()] = ADMM.shrinkage(stupdate._temp[0] / wsumu, _state.lambda() * _parms._alpha[0])
                / (denums[i+_state.activeData().numStart()] / wsumu + _state.lambda() * (1 - _parms._alpha[0]));
            }
            else if (i == 0 && !intercept){ // previous one is the last categorical variable
              int prev_level_num = _state.activeData().numStart()-_state.activeData()._catOffsets[_state.activeData()._cats-1];
              fr3.add("xjm1", _state.activeData()._adaptedFrame.vec(_state.activeData()._cats-1)); // add previous categorical variable
              if( _state.activeData()._normMul!=null){
                varold = new double []{ _state.activeData()._normMul[i]};
                meanold =  new double [] { _state.activeData()._normSub[i]};
              }
              stupdate = new GLMCoordinateDescentTaskSeqNaive(intercept, false, cat_num , new double [] {betaold[ _state.activeData().numStart()]},
                Arrays.copyOfRange(beta,_state.activeData()._catOffsets[_state.activeData()._cats-1],_state.activeData().numStart() ), null, _state.activeData()._catLvls[_state.activeData()._cats-1],
                varold, meanold, null, null, skipFirstLevel ).doAll(fr3);
              beta[_state.activeData().numStart()] = ADMM.shrinkage(stupdate._temp[0] / wsumu, _state.lambda() * _parms._alpha[0])
                / (denums[_state.activeData().numStart()] / wsumu + _state.lambda() * (1 - _parms._alpha[0]));
            }
          }
          if(_state.activeData()._nums + _state.activeData()._cats > 0) {
            // intercept update: preceded by a categorical or numeric variable
            Frame fr3 = new Frame(fr2);
            fr3.add("xjm1", _state.activeData()._adaptedFrame.vec(_state.activeData()._cats + _state.activeData()._nums - 1)); // add last variable updated in cycle to the frame
            GLMCoordinateDescentTaskSeqNaive iupdate;
            if (_state.activeData()._adaptedFrame.vec(_state.activeData()._cats + _state.activeData()._nums - 1).isCategorical()) { // only categorical vars
              cat_num = 2;
              iupdate = new GLMCoordinateDescentTaskSeqNaive(false, true, cat_num, new double[]{betaold[betaold.length - 1]},
                Arrays.copyOfRange(beta, _state.activeData()._catOffsets[_state.activeData()._cats - 1], _state.activeData()._catOffsets[_state.activeData()._cats]),
                null, _state.activeData()._catLvls[_state.activeData()._cats - 1], null, null, null, null, skipFirstLevel).doAll(fr3);
            } else { // last variable is numeric
              cat_num = 3;
              double[] meannew = null, varnew = null;
              if (_state.activeData()._normMul != null) {
                varnew = new double[]{_state.activeData()._normMul[_state.activeData()._normMul.length - 1]};
                meannew = new double[]{_state.activeData()._normSub[_state.activeData()._normSub.length - 1]};
              }
              iupdate = new GLMCoordinateDescentTaskSeqNaive(false, true, cat_num,
                new double[]{betaold[betaold.length - 1]}, new double[]{beta[beta.length - 2]}, null, null,
                null, null, varnew, meannew, skipFirstLevel).doAll(fr3);
            }
            if (_parms._intercept)
              beta[beta.length - 1] = iupdate._temp[0] / wsum;
          }
          double maxdiff = ArrayUtils.linfnorm(ArrayUtils.subtract(beta, betaold), false); // false to keep the intercept
          System.arraycopy(beta, 0, betaold, 0, beta.length);
          if (maxdiff < _parms._beta_epsilon)
            break;
        }

        double percdiff = Math.abs((objold - objVal)/objold);
        if (percdiff < _parms._objective_epsilon & iter2 >1 )
          break;
        objold=objVal;
        System.out.println("iter1 = " + iter1);
      }
      System.out.println("iter2 = " + iter2);
      long endTimeTotalNaive = System.currentTimeMillis();
      long durationTotalNaive = (endTimeTotalNaive - startTimeTotalNaive)/1000;
      System.out.println("Time to run Naive Coordinate Descent " + durationTotalNaive);
      _state._iter = iter2;
      for (Vec v : newVecs) v.remove();
      _state.updateState(beta,objold);
    }
    private void fitModel() {
      Solver solver = (_parms._solver == Solver.AUTO) ? defaultSolver() : _parms._solver;
      switch (solver) {
        case COORDINATE_DESCENT: // fall through to IRLSM
        case IRLSM:
          if(_parms._family == Family.multinomial)
            fitIRLSM_multinomial(solver);
          else if (_parms._family == Family.ordinal)
            fitIRLSM_ordinal_default(solver);
          else if(_parms._family == Family.gaussian && _parms._link == Link.identity)
            fitLSM(solver);
          else
            fitIRLSM(solver);
          break;
        case GRADIENT_DESCENT_LH:
        case GRADIENT_DESCENT_SQERR:
          if (_parms._family.equals(Family.ordinal))
            fitIRLSM_ordinal_default(solver);
          break;
        case L_BFGS:
          fitLBFGS();
          break;
        case COORDINATE_DESCENT_NAIVE:
          fitCOD();
          break;
        default:
          throw H2O.unimpl();
      }
      if(_parms._compute_p_values) { // compute p-values
        double se = 1;
        boolean seEst = false;
        double [] beta = _state.beta();

        if(_parms._family != Family.binomial && _parms._family != Family.poisson) {
          seEst = true;
          ComputeSETsk ct = new ComputeSETsk(null, _state.activeData(), _job._key, beta, _parms).doAll(_state.activeData()._adaptedFrame);
          se = ct._sumsqe / (_nobs - 1 - _state.activeData().fullN());
        }
        double [] zvalues = MemoryManager.malloc8d(_state.activeData().fullN()+1);
        Cholesky chol = _chol;
        if(_parms._standardize){ // compute non-standardized t(X)%*%W%*%X
          DataInfo activeData = _state.activeData();
          double [] beta_nostd = activeData.denormalizeBeta(beta);
          DataInfo.TransformType transform = activeData._predictor_transform;
          activeData.setPredictorTransform(DataInfo.TransformType.NONE);
          Gram g = new GLMIterationTask(_job._key,activeData,new GLMWeightsFun(_parms),beta_nostd).doAll(activeData._adaptedFrame)._gram;
          activeData.setPredictorTransform(transform); // just in case, restore the trasnform
          g.mul(_parms._obj_reg);
          chol = g.cholesky(null);
          beta = beta_nostd;
        }
        double [][] inv = chol.getInv();
        ArrayUtils.mult(inv,_parms._obj_reg*se);
        _vcov = inv;
        for(int i = 0; i < zvalues.length; ++i)
          zvalues[i] = beta[i]/Math.sqrt(inv[i][i]);
        _model.setZValues(expandVec(zvalues,_state.activeData()._activeCols,_dinfo.fullN()+1,Double.NaN),se, seEst);
      }
    }

    private long _lastScore = System.currentTimeMillis();
    private long timeSinceLastScoring(){return System.currentTimeMillis() - _lastScore;}

    private void scoreAndUpdateModel(){
      // compute full validation on train and test
      Log.info(LogMsg("Scoring after " + timeSinceLastScoring() + "ms"));
      long t1 = System.currentTimeMillis();
      Frame train = DKV.<Frame>getGet(_parms._train);
      _model.score(train).delete();
      ModelMetrics mtrain = ModelMetrics.getFromDKV(_model, train); // updated by model.scoreAndUpdateModel
      _model._output._training_metrics = mtrain;
      long t2 = System.currentTimeMillis();
      Log.info(LogMsg("Training metrics computed in " + (t2-t1) + "ms"));
      Log.info(LogMsg(mtrain.toString()));
      if(_valid != null) {
        Frame valid = DKV.<Frame>getGet(_parms._valid);
        _model.score(valid).delete();
        _model._output._validation_metrics = ModelMetrics.getFromDKV(_model, valid); //updated by model.scoreAndUpdateModel
      }
      _model._output._scoring_history = _parms._lambda_search?_lsc.to2dTable():_sc.to2dTable();
      _model.update(_job._key);
      _model.generateSummary(_parms._train,_state._iter);
      _lastScore = System.currentTimeMillis();
      long scoringTime = System.currentTimeMillis() - t1;
      _scoringInterval = Math.max(_scoringInterval,20*scoringTime); // at most 5% overhead for scoring
    }

    protected Submodel computeSubmodel(int i,double lambda) {
      Submodel sm;
      if(lambda >= _lmax && _state.l1pen() > 0)
        _model.addSubmodel(sm = new Submodel(lambda,getNullBeta(),_state._iter,_nullDevTrain,_nullDevTest));
      else {
        _model.addSubmodel(sm = new Submodel(lambda, _state.beta(),_state._iter,-1,-1));
        _state.setLambda(lambda);
        checkMemoryFootPrint(_state.activeData());
        do {
          if (_parms._family == Family.multinomial || _parms._family == Family.ordinal)
            for (int c = 0; c < _nclass; ++c)
              Log.info(LogMsg("Class " + c + " got " + _state.activeDataMultinomial(c).fullN() + " active columns out of " + _state._dinfo.fullN() + " total"));
          else
            Log.info(LogMsg("Got " + _state.activeData().fullN() + " active columns out of " + _state._dinfo.fullN() + " total"));
          fitModel();
        } while (!_state.checkKKTs());
        Log.info(LogMsg("solution has " + ArrayUtils.countNonzeros(_state.beta()) + " nonzeros"));
        if (_parms._lambda_search) {  // need train and test deviance, only "the best" submodel will be fully scored
          double trainDev = _state.deviance() / _nobs;
          double testDev = Double.NaN;
          if (_validDinfo != null) {
            if (_parms._family == Family.ordinal)
              testDev = new GLMResDevTaskOrdinal(_job._key, _validDinfo, _dinfo.denormalizeBeta(_state.beta()), _nclass).doAll(_validDinfo._adaptedFrame).avgDev();
            else
              testDev = _parms._family == Family.multinomial
                ? new GLMResDevTaskMultinomial(_job._key, _validDinfo, _dinfo.denormalizeBeta(_state.beta()), _nclass).doAll(_validDinfo._adaptedFrame).avgDev()
                : new GLMResDevTask(_job._key, _validDinfo, _parms, _dinfo.denormalizeBeta(_state.beta())).doAll(_validDinfo._adaptedFrame).avgDev();
          }
          Log.info(LogMsg("train deviance = " + trainDev + ", test deviance = " + testDev));
          double xvalDev = _xval_test_deviances == null ? -1 : _xval_test_deviances[i];
          double xvalDevSE = _xval_test_sd == null ? -1 : _xval_test_sd[i];
          _lsc.addLambdaScore(_state._iter, ArrayUtils.countNonzeros(_state.beta()), _state.lambda(), trainDev, testDev, xvalDev, xvalDevSE);
          _model.updateSubmodel(sm = new Submodel(_state.lambda(), _state.beta(), _state._iter, trainDev, testDev));
        } else // model is gonna be scored subsequently anyways
          _model.updateSubmodel(sm = new Submodel(lambda, _state.beta(), _state._iter, -1, -1));
      }
      _model.update(_job);
      return sm;
    }

    private transient double _nullDevTrain = Double.NaN;
    private transient double _nullDevTest = Double.NaN;
    @Override
    public void computeImpl() {
      if(_doInit)
        init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
      if(_parms._lambda_search) {
        if (_parms._family == Family.ordinal)
          _nullDevTrain = new GLMResDevTaskOrdinal(_job._key,_state._dinfo,getNullBeta(), _nclass).doAll(_state._dinfo._adaptedFrame).avgDev();
        else
        _nullDevTrain =  _parms._family == Family.multinomial
          ?new GLMResDevTaskMultinomial(_job._key,_state._dinfo,getNullBeta(), _nclass).doAll(_state._dinfo._adaptedFrame).avgDev()
          :new GLMResDevTask(_job._key, _state._dinfo, _parms, getNullBeta()).doAll(_state._dinfo._adaptedFrame).avgDev();
        if(_validDinfo != null) {
          if (_parms._family == Family.ordinal)
            _nullDevTest = new GLMResDevTaskOrdinal(_job._key, _validDinfo, getNullBeta(), _nclass).doAll(_validDinfo._adaptedFrame).avgDev();
          else
          _nullDevTest = _parms._family == Family.multinomial
                  ? new GLMResDevTaskMultinomial(_job._key, _validDinfo, getNullBeta(), _nclass).doAll(_validDinfo._adaptedFrame).avgDev()
                  : new GLMResDevTask(_job._key, _validDinfo, _parms, getNullBeta()).doAll(_validDinfo._adaptedFrame).avgDev();
        }
        _workPerIteration = WORK_TOTAL/_parms._nlambdas;
      } else
        _workPerIteration = 1 + (WORK_TOTAL/_parms._max_iterations);

      if((_parms._family == Family.multinomial || _parms._family == Family.ordinal) && _parms._solver != Solver.L_BFGS ) {
        double [] nb = getNullBeta();
        double maxRow = ArrayUtils.maxValue(nb);
        double sumExp = 0;
        if (_parms._family == Family.multinomial) {
          int P = _dinfo.fullN();
          int N = _dinfo.fullN() + 1;
          for (int i = 1; i < _nclass; ++i)
            sumExp += Math.exp(nb[i * N + P] - maxRow);
        }
        Vec [] vecs = _dinfo._adaptedFrame.anyVec().makeDoubles(2, new double[]{sumExp,maxRow});
        if(_parms._lambda_search && _parms._is_cv_model) {
          Scope.untrack(vecs[0]._key, vecs[1]._key);
          removeLater(vecs[0]._key,vecs[1]._key);
        }

        if (_parms._family == Family.ordinal)
          _dinfo.addResponse(new String[]{"__glm_ExpC", "__glm_ExpNPC"}, vecs); // store eta for class C and class C-1
        else
          _dinfo.addResponse(new String[]{"__glm_sumExp", "__glm_maxRow"}, vecs);
      }
      double oldDevTrain = _nullDevTrain;
      double oldDevTest = _nullDevTest;
      double [] devHistoryTrain = new double[5];
      double [] devHistoryTest = new double[5];

      if(!_parms._lambda_search)
        updateProgress(false);
      // lambda search loop
      for (int i = 0; i < _parms._lambda.length; ++i) {  // lambda search
        if(_parms._max_iterations != -1 && _state._iter >= _parms._max_iterations)
          break;
        Submodel sm = computeSubmodel(i,_parms._lambda[i]);
        double trainDev = sm.devianceTrain;
        double testDev = sm.devianceTest;
        devHistoryTest[i % devHistoryTest.length] = (oldDevTest - testDev)/oldDevTest;
        oldDevTest = testDev;
        devHistoryTrain[i % devHistoryTrain.length] = (oldDevTrain - trainDev)/oldDevTrain;
        oldDevTrain = trainDev;
        if(_parms._lambda[i] < _lmax && Double.isNaN(_lambdaCVEstimate) /** if we have cv lambda estimate we should use it, can not stop before reaching it */) {
          if (_parms._early_stopping && _state._iter >= devHistoryTrain.length) {
            double s = ArrayUtils.maxValue(devHistoryTrain);
            if (s < 1e-4) {
              Log.info(LogMsg("converged at lambda[" + i + "] = " + _parms._lambda[i] + ", improvement on train = " + s));
              break; // started overfitting
            }
            if (_validDinfo != null && _parms._nfolds <= 1) { // check for early stopping on test but only if not doing xval
              s = ArrayUtils.maxValue(devHistoryTest);
              if (s < 0) {
                Log.info(LogMsg("converged at lambda[" + i + "] = " + _parms._lambda[i] + ", improvement on test = " + s));
                break; // started overfitting
              }
            }
          }
        }
        if(_parms._lambda_search && (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval)) {
          _model._output.setSubmodelIdx(_model._output._best_lambda_idx = i);
          scoreAndUpdateModel(); // update partial results
        }
        _job.update(_workPerIteration,"iter=" + _state._iter + " lmb=" + lambdaFormatter.format(_state.lambda()) + "deviance trn/tst= " + devFormatter.format(trainDev) + "/" + devFormatter.format(testDev) + " P=" + ArrayUtils.countNonzeros(_state.beta()));
      }
      if(_state._iter >= _parms._max_iterations)
        _job.warn("Reached maximum number of iterations " + _parms._max_iterations + "!");
      if(_parms._nfolds > 1 && !Double.isNaN(_lambdaCVEstimate))
        _model._output.setSubmodel(_lambdaCVEstimate);
      else
        _model._output.pickBestModel();
      scoreAndUpdateModel();
      if(_vcov != null) {
        _model.setVcov(_vcov);
        _model.update(_job._key);
      }
      if(!(_parms)._lambda_search && _state._iter < _parms._max_iterations){
        _job.update(_workPerIteration*(_parms._max_iterations - _state._iter));
      }
      if(_iceptAdjust != 0) { // apply the intercept adjust according to prior probability
        assert _parms._intercept;
        double [] b = _model._output._global_beta;
        b[b.length-1] += _iceptAdjust;
        for(Submodel sm:_model._output._submodels)
          sm.beta[sm.beta.length-1] += _iceptAdjust;
        _model.update(_job._key);
      }
      doCleanup();
    }

    @Override public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller){
      doCleanup();
      return true;
    }


    @Override public boolean progress(double [] beta, GradientInfo ginfo) {
      _state._iter++;
      if(ginfo instanceof ProximalGradientInfo) {
        ginfo = ((ProximalGradientInfo) ginfo)._origGinfo;
        GLMGradientInfo gginfo = (GLMGradientInfo) ginfo;
        _state.updateState(beta, gginfo);
        if (!_parms._lambda_search)
          updateProgress(false);
        return !timeout() && !_job.stop_requested() && _state._iter < _parms._max_iterations;
      } else {
        GLMGradientInfo gginfo = (GLMGradientInfo) ginfo;
        if(gginfo._gradient == null)
          _state.updateState(beta,gginfo._likelihood);
        else
          _state.updateState(beta, gginfo);
        if (!_parms._lambda_search)
          updateProgress(true);
        boolean converged = _state.converged();
        if (converged) Log.info(LogMsg(_state.convergenceMsg));
        return !timeout() && !_job.stop_requested() && !converged && _state._iter < _parms._max_iterations;
      }
    }

    public boolean progress(double [] beta, double likelihood) {
      _state._iter++;
      _state.updateState(beta,likelihood);
      if(!_parms._lambda_search)
        updateProgress(true);
      boolean converged = _state.converged();
      if(converged) Log.info(LogMsg(_state.convergenceMsg));
      return !_job.stop_requested() && !converged && _state._iter < _parms._max_iterations ;
    }

    private transient long _scoringInterval = SCORING_INTERVAL_MSEC;

    // update user visible progress
    protected void updateProgress(boolean canScore){
      assert !_parms._lambda_search;
      _sc.addIterationScore(_state._iter, _state.likelihood(), _state.objective());
      _job.update(_workPerIteration,_state.toString());
      if(canScore && (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval)) {
        _model.update(_state.expandBeta(_state.beta()), -1, -1, _state._iter);
        scoreAndUpdateModel();
      }
    }
  }

  private Solver defaultSolver() {
    Solver s = Solver.IRLSM;
    int max_active = 0;
    if(_parms._family == Family.multinomial )
      for(int c = 0; c < _nclass; ++c)
        max_active += _state.activeDataMultinomial(c).fullN();
    else max_active = _state.activeData().fullN();
    if(max_active >= 5000) // cutoff has to be somewhere
      s = Solver.L_BFGS;
    else if(_parms._lambda_search) { // lambda search prefers coordinate descent
      // l1 lambda search is better with coordinate descent!
      s = Solver.COORDINATE_DESCENT;
    } else if(_state.activeBC().hasBounds() && !_state.activeBC().hasProximalPenalty()) {
      s = Solver.COORDINATE_DESCENT;
    } else if(_parms._family == Family.multinomial && _parms._alpha[0] == 0)
      s = Solver.L_BFGS; // multinomial does better with lbfgs
    else
      Log.info(LogMsg("picked solver " + s));
    if(s != Solver.L_BFGS && _parms._max_active_predictors == -1)
      _parms._max_active_predictors = 5000;
    _parms._solver = s;
    return s;
  }

  double objVal(double likelihood, double[] beta, double lambda) {
    double alpha = _parms._alpha[0];
    double proximalPen = 0;
    BetaConstraint bc = _state.activeBC();
    if (_state.activeBC()._betaGiven != null && bc._rho != null) {
      for (int i = 0; i < bc._betaGiven.length; ++i) {
        double diff = beta[i] - bc._betaGiven[i];
        proximalPen += diff * diff * bc._rho[i];
      }
    }
    return (likelihood * _parms._obj_reg
      + .5 * proximalPen
      + lambda * (alpha * ArrayUtils.l1norm(beta, _parms._intercept)
      + (1 - alpha) * .5 * ArrayUtils.l2norm2(beta, _parms._intercept)));
  }


  private String  LogMsg(String msg) {return "GLM[dest=" + dest() + ", " + _state + "] " + msg;}


  private static final double[] expandVec(double[] beta, final int[] activeCols, int fullN) {
    return expandVec(beta, activeCols, fullN, 0);
  }

  private static final double[] expandVec(double[] beta, final int[] activeCols, int fullN, double filler) {
    assert beta != null;
    if (activeCols == null) return beta;
    double[] res = MemoryManager.malloc8d(fullN);
    Arrays.fill(res, filler);
    int i = 0;
    for (int c : activeCols)
      res[c] = beta[i++];
    res[res.length - 1] = beta[beta.length - 1];
    return res;
  }

  private static double [] doUpdateCD(double [] grads, double [] ary, double diff , int variable_min, int variable_max) {
    for (int i = 0; i < variable_min; i++)
      grads[i] += diff * ary[i];
    for (int i = variable_max; i < grads.length; i++)
      grads[i] += diff * ary[i];
    return grads;
  }

  public double [] COD_solve(ComputationState.GramXY gram, double alpha, double lambda) {
    double [] res = COD_solve(gram.gram.getXX(),gram.xy,gram.getCODGradients(),gram.newCols,alpha,lambda);
    gram.newCols = new int[0];
    return res;
  }

  private double [] COD_solve(double [][] xx, double [] xy, double [] grads, int [] newCols, double alpha, double lambda) {
    double wsumInv = 1.0/(xx[xx.length-1][xx.length-1]);
    final double betaEpsilon = _parms._beta_epsilon*_parms._beta_epsilon;
    double updateEpsilon = 0.01*betaEpsilon;
    double l1pen = lambda * alpha;
    double l2pen = lambda*(1-alpha);
    double [] diagInv = MemoryManager.malloc8d(xx.length);
    for(int i = 0; i < diagInv.length; ++i)
      diagInv[i] = 1.0/(xx[i][i] + l2pen);
    DataInfo activeData = _state.activeData();
    int [][] nzs = new int[activeData.numStart()][];
    int sparseCnt = 0;
    if(nzs.length > 1000) {
      final int [] nzs_ary = new int[xx.length];
      for (int i = 0; i < activeData._cats; ++i) {
        int var_min = activeData._catOffsets[i];
        int var_max = activeData._catOffsets[i + 1];
        for(int l = var_min; l < var_max; ++l) {
          int k = 0;
          double [] x = xx[l];
          for (int j = 0; j < var_min; ++j)
            if (x[j] != 0) nzs_ary[k++] = j;
          for (int j = var_max; j < activeData.numStart(); ++j)
            if (x[j] != 0) nzs_ary[k++] = j;
          if (k < ((nzs_ary.length - var_max + var_min) >> 3)) {
            sparseCnt++;
            nzs[l] = Arrays.copyOf(nzs_ary, k);
          }
        }
      }
    }
    final BetaConstraint bc = _state.activeBC();
    double [] beta = _state.beta().clone();
    int numStart = activeData.numStart();
    if(newCols != null) {
      for (int id : newCols) {
        double b = bc.applyBounds(ADMM.shrinkage(grads[id], l1pen) * diagInv[id], id);
        if (b != 0) {
          doUpdateCD(grads, xx[id], -b, id, id + 1);
          beta[id] = b;
        }
      }
    }
    int iter1 = 0;
    int P = xy.length - 1;
    double maxDiff = 0;
//    // CD loop
    while (iter1++ < Math.max(P,500)) { 
      maxDiff = 0;
      for (int i = 0; i < activeData._cats; ++i) {
        for(int j = activeData._catOffsets[i]; j < activeData._catOffsets[i+1]; ++j) { // can do in parallel
          double b = bc.applyBounds(ADMM.shrinkage(grads[j], l1pen) * diagInv[j],j); // new beta value here
          double bd = beta[j] - b;
          if(bd != 0) {
            double diff = bd*bd*xx[j][j];
            if(diff > maxDiff) maxDiff = diff;
            if (nzs[j] == null)
              doUpdateCD(grads, xx[j], bd, activeData._catOffsets[i], activeData._catOffsets[i + 1]);
            else {
              double[] x = xx[j];
              int[] ids = nzs[j];
              for (int id : ids) grads[id] += bd * x[id];
              doUpdateCD(grads, x, bd, 0, activeData.numStart());
            }
            beta[j] = b;
          }
        }
      }
      for (int i = numStart; i < P; ++i) {
        double b = bc.applyBounds(ADMM.shrinkage(grads[i], l1pen) * diagInv[i],i);
        double bd = beta[i] - b;
        double diff = bd * bd * xx[i][i];
        if (diff > maxDiff) maxDiff = diff;
        if(diff > updateEpsilon) {
          doUpdateCD(grads, xx[i], bd, i, i + 1);
          beta[i] = b;
        }
      }
      // intercept
      if(_parms._intercept) {
        double b = bc.applyBounds(grads[P] * wsumInv,P);
        double bd = beta[P] - b;
        double diff = bd * bd * xx[P][P];
        if (diff > maxDiff) maxDiff = diff;
        doUpdateCD(grads, xx[P], bd, P, P + 1);
        beta[P] = b;
      }
      if (maxDiff < betaEpsilon) // stop if beta not changing much
        break;
    }
    long tend = System.currentTimeMillis();
    return beta;
  }

  /**
   * Created by tomasnykodym on 3/30/15.
   */
  public static final class GramSolver implements ProximalSolver {
    private final Gram _gram;
    private Cholesky _chol;

    private final double[] _xy;
    final double _lambda;
    double[] _rho;
    boolean _addedL2;
    double _betaEps;

    private static double boundedX(double x, double lb, double ub) {
      if (x < lb) x = lb;
      if (x > ub) x = ub;
      return x;
    }

    public GramSolver(Gram gram, double[] xy, double lmax, double betaEps, boolean intercept) {
      _gram = gram;
      _lambda = 0;
      _betaEps = betaEps;
      _xy = xy;
      double[] rhos = MemoryManager.malloc8d(xy.length);
      computeCholesky(gram, rhos, lmax * 1e-8,intercept);
      _addedL2 = rhos[0] != 0;
      _rho = _addedL2 ? rhos : null;
    }

    // solve non-penalized problem
    public void solve(double[] result) {
      System.arraycopy(_xy, 0, result, 0, _xy.length);
      _chol.solve(result);
      double gerr = Double.POSITIVE_INFINITY;
      if (_addedL2) { // had to add l2-pen to turn the gram to be SPD
        double[] oldRes = MemoryManager.arrayCopyOf(result, result.length);
        for (int i = 0; i < 1000; ++i) {
          solve(oldRes, result);
          double[] g = gradient(result)._gradient;
          gerr = Math.max(-ArrayUtils.minValue(g), ArrayUtils.maxValue(g));
          if (gerr < 1e-4) return;
          System.arraycopy(result, 0, oldRes, 0, result.length);
        }
        Log.warn("Gram solver did not converge, gerr = " + gerr);
      }
    }

    public GramSolver(Gram gram, double[] xy, boolean intercept, double l2pen, double l1pen, double[] beta_given, double[] proxPen, double[] lb, double[] ub) {
      if (ub != null && lb != null)
        for (int i = 0; i < ub.length; ++i) {
          assert ub[i] >= lb[i] : i + ": ub < lb, ub = " + Arrays.toString(ub) + ", lb = " + Arrays.toString(lb);
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
      int ii = intercept ? 1 : 0;
      int icptCol = gram.fullN()-1;
      double[] rhos = MemoryManager.malloc8d(xy.length);
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
        double x = ((y - ybar * xbar) / ((gram.get(i, i) - xbar * xbar) + l2pen));///gram.get(i,i);
        rhos[i] = ADMM.L1Solver.estimateRho(x, l1pen, lb == null ? Double.NEGATIVE_INFINITY : lb[i], ub == null ? Double.POSITIVE_INFINITY : ub[i]);
      }
      // do the intercept separate as l1pen does not apply to it
      if (intercept && (lb != null && !Double.isInfinite(lb[icptCol]) || ub != null && !Double.isInfinite(ub[icptCol]))) {
        int icpt = xy.length - 1;
        rhos[icpt] = 1;//(xy[icpt] >= 0 ? xy[icpt] : -xy[icpt]);
      }
      if (l2pen > 0)
        gram.addDiag(l2pen);
      if (proxPen != null && beta_given != null) {
        gram.addDiag(proxPen);
        xy = xy.clone();
        for (int i = 0; i < xy.length; ++i)
          xy[i] += proxPen[i] * beta_given[i];
      }
      _xy = xy;
      _rho = rhos;
      computeCholesky(gram, rhos, 1e-5,intercept);
    }

    private void computeCholesky(Gram gram, double[] rhos, double rhoAdd, boolean intercept) {
      gram.addDiag(rhos);
      if(!intercept) {
        gram.dropIntercept();
        rhos = Arrays.copyOf(rhos,rhos.length-1);
        _xy[_xy.length-1] = 0;
      }
      _chol = gram.cholesky(null, true, null);
      if (!_chol.isSPD()) { // make sure rho is big enough
        gram.addDiag(ArrayUtils.mult(rhos, -1));
        gram.addDiag(rhoAdd,!intercept);
        Log.info("Got NonSPD matrix with original rho, re-computing with rho = " + (_rho[0]+rhoAdd));
        _chol = gram.cholesky(null, true, null);
        int cnt = 0;
        double rhoAddSum = rhoAdd;
        while (!_chol.isSPD() && cnt++ < 5) {
          gram.addDiag(rhoAdd,!intercept);
          rhoAddSum += rhoAdd;
          Log.warn("Still NonSPD matrix, re-computing with rho = " + (rhos[0] + rhoAddSum));
          _chol = gram.cholesky(null, true, null);
        }
        if (!_chol.isSPD())
          throw new NonSPDMatrixException();
      }
      gram.addDiag(ArrayUtils.mult(rhos, -1));
      ArrayUtils.mult(rhos, -1);
    }

    @Override
    public double[] rho() {
      return _rho;
    }

    @Override
    public boolean solve(double[] beta_given, double[] result) {
      if (beta_given != null)
        for (int i = 0; i < _xy.length; ++i)
          result[i] = _xy[i] + _rho[i] * beta_given[i];
      else
        System.arraycopy(_xy, 0, result, 0, _xy.length);
      _chol.solve(result);
      return true;
    }

    @Override
    public boolean hasGradient() {
      return false;
    }

    @Override
    public GradientInfo gradient(double[] beta) {
      double[] grad = _gram.mul(beta);
      for (int i = 0; i < _xy.length; ++i)
        grad[i] -= _xy[i];
      return new GradientInfo(Double.NaN,grad); // todo compute the objective
    }

    @Override
    public int iter() {
      return 0;
    }
  }


  public static class ProximalGradientInfo extends GradientInfo {
    final GradientInfo _origGinfo;


    public ProximalGradientInfo(GradientInfo origGinfo, double objVal, double[] gradient) {
      super(objVal, gradient);
      _origGinfo = origGinfo;
    }
  }

  /**
   * Simple wrapper around ginfo computation, adding proximal penalty
   */
  public static class ProximalGradientSolver implements GradientSolver, ProximalSolver {
    final GradientSolver _solver;
    double[] _betaGiven;
    double[] _beta;
    private ProximalGradientInfo _ginfo;
    private final ProgressMonitor _pm;
    final double[] _rho;
    private final double _objEps;
    private final double _gradEps;

    public ProximalGradientSolver(GradientSolver s, double[] betaStart, double[] rho, double objEps, double gradEps, GradientInfo ginfo,ProgressMonitor pm) {
      super();
      _solver = s;
      _rho = rho;
      _objEps = objEps;
      _gradEps = gradEps;
      _pm = pm;
      _beta = betaStart;
      _betaGiven = MemoryManager.malloc8d(betaStart.length);
//      _ginfo = new ProximalGradientInfo(ginfo,ginfo._objVal,ginfo._gradient);
    }

    public static double proximal_gradient(double[] grad, double obj, double[] beta, double[] beta_given, double[] rho) {
      for (int i = 0; i < beta.length; ++i) {
        double diff = (beta[i] - beta_given[i]);
        double pen = rho[i] * diff;
        if(grad != null)
          grad[i] += pen;
        obj += .5 * pen * diff;
      }
      return obj;
    }

    private ProximalGradientInfo computeProxGrad(GradientInfo ginfo, double [] beta) {
      assert !(ginfo instanceof ProximalGradientInfo);
      double[] gradient = ginfo._gradient.clone();
      double obj = proximal_gradient(gradient, ginfo._objVal, beta, _betaGiven, _rho);
      return new ProximalGradientInfo(ginfo, obj, gradient);
    }
    @Override
    public ProximalGradientInfo getGradient(double[] beta) {
      return computeProxGrad(_solver.getGradient(beta),beta);
    }

    @Override
    public GradientInfo getObjective(double[] beta) {
      GradientInfo ginfo = _solver.getObjective(beta);
      double obj = proximal_gradient(null, ginfo._objVal, beta, _betaGiven, _rho);
      return new ProximalGradientInfo(ginfo,obj,null);
    }

    @Override
    public double[] rho() {
      return _rho;
    }

    private int _iter;

    @Override
    public boolean solve(double[] beta_given, double[] beta) {
      GradientInfo origGinfo = (_ginfo == null || !Arrays.equals(_beta,beta))
          ?_solver.getGradient(beta)
          :_ginfo._origGinfo;
      System.arraycopy(beta_given,0,_betaGiven,0,beta_given.length);
      L_BFGS.Result r = new L_BFGS().setObjEps(_objEps).setGradEps(_gradEps).solve(this, beta, _ginfo = computeProxGrad(origGinfo,beta), _pm);
      System.arraycopy(r.coefs,0,beta,0,r.coefs.length);
      _beta = r.coefs;
      _iter += r.iter;
      _ginfo = (ProximalGradientInfo) r.ginfo;
      return r.converged;
    }

    @Override
    public boolean hasGradient() {
      return true;
    }

    @Override
    public GradientInfo gradient(double[] beta) {
      return getGradient(beta)._origGinfo;
    }

    @Override
    public int iter() {
      return _iter;
    }
  }

  public static class GLMGradientInfo extends GradientInfo {
    final double _likelihood;

    public GLMGradientInfo(double likelihood, double objVal, double[] grad) {
      super(objVal, grad);
      _likelihood = likelihood;
    }

    public String toString(){
      return "GLM grad info: likelihood = " + _likelihood + super.toString();
    }
  }
  
  /**
   * Gradient and line search computation for L_BFGS and also L_BFGS solver wrapper (for ADMM)
   */
  public static final class GLMGradientSolver implements GradientSolver {
    final GLMParameters _parms;
    final DataInfo _dinfo;
    final BetaConstraint _bc;
    final double _l2pen; // l2 penalty
    double[][] _betaMultinomial;
    final Job _job;

    public GLMGradientSolver(Job job, GLMParameters glmp, DataInfo dinfo, double l2pen, BetaConstraint bc) {
      _job = job;
      _bc = bc;
      _parms = glmp;
      _dinfo = dinfo;
      _l2pen = l2pen;
    }

    /*
    Only update the likelihood function for multinomial while leaving all else stale and old.  This is only
    used by multinomial with COD.
     */
    public GLMGradientInfo getMultinomialLikelihood(double[] beta) {
      assert _parms._family.equals(Family.multinomial) : "GLMGradientInfo.getMultinomialLikelihood is only used by multinomial GLM";
      assert _betaMultinomial != null : "Multinomial coefficents cannot be null.";

      int off = 0;
      for (int i = 0; i < _betaMultinomial.length; ++i) {
        System.arraycopy(beta, off, _betaMultinomial[i], 0, _betaMultinomial[i].length);
        off += _betaMultinomial[i].length;
      }
      GLMMultinomialGradientBaseTask gt = new GLMMultinomialLikelihoodTask(_job, _dinfo, _l2pen, _betaMultinomial,
              _parms).doAll(_dinfo._adaptedFrame);
      double l2pen = 0;
      for (double[] b : _betaMultinomial) {
        l2pen += ArrayUtils.l2norm2(b, _dinfo._intercept);
      }
      return new GLMGradientInfo(gt._likelihood, gt._likelihood * _parms._obj_reg + .5 * _l2pen * l2pen, null);
    }

    @Override
    public GLMGradientInfo getGradient(double[] beta) {
      if (_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
        if (_betaMultinomial == null) {
          int nclasses = beta.length / (_dinfo.fullN() + 1);
          assert beta.length % (_dinfo.fullN() + 1) == 0:"beta len = " + beta.length + ", fullN +1  == " + (_dinfo.fullN()+1);
          _betaMultinomial = new double[nclasses][];
          for (int i = 0; i < nclasses; ++i)
            _betaMultinomial[i] = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        }
        int off = 0;
        for (int i = 0; i < _betaMultinomial.length; ++i) {
          System.arraycopy(beta, off, _betaMultinomial[i], 0, _betaMultinomial[i].length);
          off += _betaMultinomial[i].length;
        }
        GLMMultinomialGradientBaseTask gt = new GLMMultinomialGradientTask(_job, _dinfo, _l2pen, _betaMultinomial,
                _parms).doAll(_dinfo._adaptedFrame);
        double l2pen = 0;
        for (double[] b : _betaMultinomial) {
          l2pen += ArrayUtils.l2norm2(b, _dinfo._intercept);

          if (_parms._family == Family.ordinal)
            break;  // only one beta for all classes, l2pen needs to count beta for one class only
        }

        double[] grad = gt.gradient();
        if (!_parms._intercept) {
          for (int i = _dinfo.fullN(); i < beta.length; i += _dinfo.fullN() + 1)
            grad[i] = 0;
        }
        return new GLMGradientInfo(gt._likelihood, gt._likelihood * _parms._obj_reg + .5 * _l2pen * l2pen, grad);
      } else {
        assert beta.length == _dinfo.fullN() + 1;
        assert _parms._intercept || (beta[beta.length-1] == 0);
        GLMGradientTask gt;
        if(_parms._family == Family.binomial && _parms._link == Link.logit)
          gt = new GLMBinomialGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta).doAll(_dinfo._adaptedFrame);
        else if(_parms._family == Family.gaussian && _parms._link == Link.identity)
          gt = new GLMGaussianGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta).doAll(_dinfo._adaptedFrame);
        else if(_parms._family == Family.poisson && _parms._link == Link.log)
          gt = new GLMPoissonGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta).doAll(_dinfo._adaptedFrame);
        else if(_parms._family == Family.quasibinomial)
          gt = new GLMQuasiBinomialGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta).doAll(_dinfo._adaptedFrame);
        else
          gt = new GLMGenericGradientTask(_job == null?null:_job._key, _dinfo, _parms, _l2pen, beta).doAll(_dinfo._adaptedFrame);
        double [] gradient = gt._gradient;
        double  likelihood = gt._likelihood;
        if (!_parms._intercept) // no intercept, null the ginfo
          gradient[gradient.length - 1] = 0;
        double obj = likelihood * _parms._obj_reg + .5 * _l2pen * ArrayUtils.l2norm2(beta, true);
        if (_bc != null && _bc._betaGiven != null && _bc._rho != null)
          obj = ProximalGradientSolver.proximal_gradient(gradient, obj, beta, _bc._betaGiven, _bc._rho);
        return new GLMGradientInfo(likelihood, obj, gradient);
      }
    }

    @Override
    public GradientInfo getObjective(double[] beta) {
      double l = new GLMResDevTask(_job._key,_dinfo,_parms,beta).doAll(_dinfo._adaptedFrame)._likelihood;
      return new GLMGradientInfo(l,l*_parms._obj_reg + .5*_l2pen*ArrayUtils.l2norm2(beta,true),null);
    }
  }

  protected static double sparseOffset(double[] beta, DataInfo dinfo) {
    double etaOffset = 0;
    if (dinfo._normMul != null && dinfo._normSub != null && beta != null) {
      int ns = dinfo.numStart();
      for (int i = 0; i < dinfo._nums; ++i)
        etaOffset -= beta[i + ns] * dinfo._normSub[i] * dinfo._normMul[i];
    }
    return etaOffset;
  }


  public final class BetaConstraint extends Iced {
    double[] _betaStart;
    double[] _betaGiven;
    double[] _rho;
    double[] _betaLB;
    double[] _betaUB;

    public BetaConstraint() {
      if (_parms._non_negative) setNonNegative();
    }

    public void setNonNegative() {
      if (_betaLB == null) {
        _betaLB = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        _betaLB[_dinfo.fullN()] = Double.NEGATIVE_INFINITY;
      } else for (int i = 0; i < _betaLB.length - 1; ++i)
        _betaLB[i] = Math.max(0, _betaLB[i]);
      if (_betaUB == null) {
        _betaUB = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        Arrays.fill(_betaUB, Double.POSITIVE_INFINITY);
      }
    }

    public double applyBounds(double d, int i) {
      if(_betaLB != null && d < _betaLB[i])
        return _betaLB[i];
      if(_betaUB != null && d > _betaUB[i])
        return _betaUB[i];
      return d;
    }

    public BetaConstraint(Frame beta_constraints) {
      Vec v = beta_constraints.vec("names");
      String[] dom;
      int[] map;
      if (v.isString()) {
        dom = new String[(int) v.length()];
        map = new int[dom.length];
        BufferedString tmpStr = new BufferedString();
        for (int i = 0; i < dom.length; ++i) {
          dom[i] = v.atStr(tmpStr, i).toString();
          map[i] = i;
        }
        // check for dups
        String[] sortedDom = dom.clone();
        Arrays.sort(sortedDom);
        for (int i = 1; i < sortedDom.length; ++i)
          if (sortedDom[i - 1].equals(sortedDom[i]))
            throw new IllegalArgumentException("Illegal beta constraints file, got duplicate constraint for predictor '" + sortedDom[i - 1] + "'!");
      } else if (v.isCategorical()) {
        dom = v.domain();
        map = FrameUtils.asInts(v);
        // check for dups
        int[] sortedMap = MemoryManager.arrayCopyOf(map, map.length);
        Arrays.sort(sortedMap);
        for (int i = 1; i < sortedMap.length; ++i)
          if (sortedMap[i - 1] == sortedMap[i])
            throw new IllegalArgumentException("Illegal beta constraints file, got duplicate constraint for predictor '" + dom[sortedMap[i - 1]] + "'!");
      } else
        throw new IllegalArgumentException("Illegal beta constraints file, names column expected to contain column names (strings)");
      // for now only categoricals allowed here
      String[] names = ArrayUtils.append(_dinfo.coefNames(), "Intercept");
      if (!Arrays.deepEquals(dom, names)) { // need mapping
        HashMap<String, Integer> m = new HashMap<String, Integer>();
        for (int i = 0; i < names.length; ++i)
          m.put(names[i], i);
        int[] newMap = MemoryManager.malloc4(dom.length);
        for (int i = 0; i < map.length; ++i) {
          if (_removedCols.contains(dom[map[i]])) {
            newMap[i] = -1;
            continue;
          }
          Integer I = m.get(dom[map[i]]);
          if (I == null) {
            throw new IllegalArgumentException("Unrecognized coefficient name in beta-constraint file, unknown name '" + dom[map[i]] + "'");
          }
          newMap[i] = I;
        }
        map = newMap;
      }
      final int numoff = _dinfo.numStart();
      String[] valid_col_names = new String[]{"names", "beta_given", "beta_start", "lower_bounds", "upper_bounds", "rho", "mean", "std_dev"};
      Arrays.sort(valid_col_names);
      for (String s : beta_constraints.names())
        if (Arrays.binarySearch(valid_col_names, s) < 0)
          error("beta_constraints", "Unknown column name '" + s + "'");
      if ((v = beta_constraints.vec("beta_start")) != null) {
        _betaStart = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
        for (int i = 0; i < (int) v.length(); ++i)
          if (map[i] != -1)
            _betaStart[map[i]] = v.at(i);
      }
      if ((v = beta_constraints.vec("beta_given")) != null) {
        _betaGiven = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
        for (int i = 0; i < (int) v.length(); ++i)
          if (map[i] != -1)
            _betaGiven[map[i]] = v.at(i);
      }
      if ((v = beta_constraints.vec("upper_bounds")) != null) {
        _betaUB = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
        Arrays.fill(_betaUB, Double.POSITIVE_INFINITY);
        for (int i = 0; i < (int) v.length(); ++i)
          if (map[i] != -1)
            _betaUB[map[i]] = v.at(i);
      }
      if ((v = beta_constraints.vec("lower_bounds")) != null) {
        _betaLB = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
        Arrays.fill(_betaLB, Double.NEGATIVE_INFINITY);
        for (int i = 0; i < (int) v.length(); ++i)
          if (map[i] != -1)
            _betaLB[map[i]] = v.at(i);
      }
      if ((v = beta_constraints.vec("rho")) != null) {
        _rho = MemoryManager.malloc8d(_dinfo.fullN() + (_dinfo._intercept ? 1 : 0));
        for (int i = 0; i < (int) v.length(); ++i)
          if (map[i] != -1)
            _rho[map[i]] = v.at(i);
      }
      // mean override (for data standardization)
      if ((v = beta_constraints.vec("mean")) != null) {
        _parms._stdOverride = true;
        for (int i = 0; i < v.length(); ++i) {
          if (!v.isNA(i) && map[i] != -1) {
            int idx = map == null ? i : map[i];
            if (idx > _dinfo.numStart() && idx < _dinfo.fullN()) {
              _dinfo._normSub[idx - _dinfo.numStart()] = v.at(i);
            } else {
              // categorical or Intercept, will be ignored
            }
          }
        }
      }
      // standard deviation override (for data standardization)
      if ((v = beta_constraints.vec("std_dev")) != null) {
        _parms._stdOverride = true;
        for (int i = 0; i < v.length(); ++i) {
          if (!v.isNA(i) && map[i] != -1) {
            int idx = map == null ? i : map[i];
            if (idx > _dinfo.numStart() && idx < _dinfo.fullN()) {
              _dinfo._normMul[idx - _dinfo.numStart()] = 1.0 / v.at(i);
            } else {
              // categorical or Intercept, will be ignored
            }
          }
        }
      }
      if (_dinfo._normMul != null) {
        double normG = 0, normS = 0, normLB = 0, normUB = 0;
        for (int i = numoff; i < _dinfo.fullN(); ++i) {
          double s = _dinfo._normSub[i - numoff];
          double d = 1.0 / _dinfo._normMul[i - numoff];
          if (_betaUB != null && !Double.isInfinite(_betaUB[i])) {
            normUB *= s;
            _betaUB[i] *= d;
          }
          if (_betaLB != null && !Double.isInfinite(_betaUB[i])) {
            normLB *= s;
            _betaLB[i] *= d;
          }
          if (_betaGiven != null) {
            normG += _betaGiven[i] * s;
            _betaGiven[i] *= d;
          }
          if (_betaStart != null) {
            normS += _betaStart[i] * s;
            _betaStart[i] *= d;
          }
        }
        if (_dinfo._intercept) {
          int n = _dinfo.fullN();
          if (_betaGiven != null)
            _betaGiven[n] += normG;
          if (_betaStart != null)
            _betaStart[n] += normS;
          if (_betaLB != null)
            _betaLB[n] += normLB;
          if (_betaUB != null)
            _betaUB[n] += normUB;
        }
      }
      if (_betaStart == null && _betaGiven != null)
        _betaStart = _betaGiven.clone();
      if (_betaStart != null) {
        if (_betaLB != null || _betaUB != null) {
          for (int i = 0; i < _betaStart.length; ++i) {
            if (_betaLB != null && _betaLB[i] > _betaStart[i])
              _betaStart[i] = _betaLB[i];
            if (_betaUB != null && _betaUB[i] < _betaStart[i])
              _betaStart[i] = _betaUB[i];
          }
        }
      }
      if (_parms._non_negative) setNonNegative();
      check();
    }

    public String toString() {
      double[][] ary = new double[_betaGiven.length][3];

      for (int i = 0; i < _betaGiven.length; ++i) {
        ary[i][0] = _betaGiven[i];
        ary[i][1] = _betaLB[i];
        ary[i][2] = _betaUB[i];
      }
      return ArrayUtils.pprint(ary);
    }

    public boolean hasBounds() {
      if (_betaLB != null)
        for (double d : _betaLB)
          if (!Double.isInfinite(d)) return true;
      if (_betaUB != null)
        for (double d : _betaUB)
          if (!Double.isInfinite(d)) return true;
      return false;
    }

    public boolean hasProximalPenalty() {
      return _betaGiven != null && _rho != null && ArrayUtils.countNonzeros(_rho) > 0;
    }

    public void adjustGradient(double[] beta, double[] grad) {
      if (_betaGiven != null && _rho != null) {
        for (int i = 0; i < _betaGiven.length; ++i) {
          double diff = beta[i] - _betaGiven[i];
          grad[i] += _rho[i] * diff;
        }
      }
    }

    double proxPen(double[] beta) {
      double res = 0;
      if (_betaGiven != null && _rho != null) {
        for (int i = 0; i < _betaGiven.length; ++i) {
          double diff = beta[i] - _betaGiven[i];
          res += _rho[i] * diff * diff;
        }
        res *= .5;
      }
      return res;
    }

    public void check() {
      if (_betaLB != null && _betaUB != null)
        for (int i = 0; i < _betaLB.length; ++i)
          if (!(_betaLB[i] <= _betaUB[i]))
            throw new IllegalArgumentException("lower bounds must be <= upper bounds, " + _betaLB[i] + " !<= " + _betaUB[i]);
    }

    public BetaConstraint filterExpandedColumns(int[] activeCols) {
      BetaConstraint res = new BetaConstraint();
      if (_betaLB != null)
        res._betaLB = ArrayUtils.select(_betaLB, activeCols);
      if (_betaUB != null)
        res._betaUB = ArrayUtils.select(_betaUB, activeCols);
      if (_betaGiven != null)
        res._betaGiven = ArrayUtils.select(_betaGiven, activeCols);
      if (_rho != null)
        res._rho = ArrayUtils.select(_rho, activeCols);
      if (_betaStart != null)
        res._betaStart = ArrayUtils.select(_betaStart, activeCols);
      return res;
    }
  }


}
