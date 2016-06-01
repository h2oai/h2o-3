package hex.glm;

import hex.*;
import hex.deeplearning.DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
import hex.glm.ComputationState.GLMSubsetGinfo;
import hex.glm.GLMModel.*;
import hex.optimization.ADMM.L1Solver;
import hex.optimization.L_BFGS;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.glm.GLMModel.GLMParameters.Solver;
import hex.glm.GLMTask.*;
import hex.gram.Gram;
import hex.gram.Gram.Cholesky;
import hex.gram.Gram.NonSPDMatrixException;
import hex.optimization.ADMM;
import hex.optimization.ADMM.ProximalSolver;
import hex.optimization.L_BFGS.*;
import hex.optimization.OptimizationUtils.*;
import jsr166y.CountedCompleter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.*;
import water.parser.BufferedString;
import water.util.*;
import water.util.ArrayUtils;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 */
public class GLM extends ModelBuilder<GLMModel,GLMParameters,GLMOutput> {

  static NumberFormat lambdaFormatter = new DecimalFormat(".##E0");
  static NumberFormat devFormatter = new DecimalFormat(".##");

  public static final int SCORING_INTERVAL_MSEC = 15000; // scoreAndUpdateModel every minute unless socre every iteration is set
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

  private double _lambdaCVEstimate = Double.NaN;
  private boolean _doInit = true;
  private double [] _xval_test_deviances;
  @Override
  public void modifyParmsForCrossValidationMainModel(ModelBuilder[] cvModelBuilders) {
    if(_parms._lambda_search) {
      double lambdaAvg = 0;
      double lambdaSE = 0;
      for (int i = 0; i < cvModelBuilders.length; ++i)
        lambdaAvg += ((GLM) cvModelBuilders[i])._model._output.bestSubmodel().lambda_value;

      lambdaAvg /= cvModelBuilders.length;
      for (int i = 0; i < cvModelBuilders.length; ++i) {
        double diff = lambdaAvg - ((GLM) cvModelBuilders[i])._model._output.bestSubmodel().lambda_value;
        lambdaSE += diff * diff;
      }
      lambdaSE = lambdaAvg / ((cvModelBuilders.length - 1) * Math.sqrt(cvModelBuilders.length));
      double lambdaCVEstimate = lambdaAvg + lambdaSE;
      int j = _parms._lambda.length-1;
      while(j > 0 && _parms._lambda[j-1] < lambdaCVEstimate)j--;
      _lambdaCVEstimate = _parms._lambda[j];
      if(_parms._early_stopping) _parms._lambda = Arrays.copyOf(_parms._lambda,j+1);
      _xval_test_deviances = new double[j+1];
      for(int i = 0; i < cvModelBuilders.length; ++i) {
        GLM g = (GLM)cvModelBuilders[i];
        for(int k = 0; k <= j; ++k) {
          double l = _parms._lambda[k];
          if (g._model._output.getSubmodel(l) == null)
            g._driver.computeSubmodel(k,l);
          _xval_test_deviances[k] += g._model._output.getSubmodel(l).devianceTest;
        }
        g._model._output.setSubmodel(_lambdaCVEstimate);
        DKV.put(g._model);
      }
      Log.info("lambdaCV avg = " + lambdaAvg + ", standard error = " + lambdaSE + ", " + "lambdaEstimate = " + _lambdaCVEstimate);
    }
    _parms._early_stopping = false;
    _doInit = false;
  }

  @Override
  protected void checkMemoryFootPrint() {/* see below */ }

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

  private int _lambdaId;
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
      String[] cnames = new String[]{"timestamp", "duration", "iteration", "negative_log_likelihood", "objective"};
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

    public LambdaSearchScoringHistory(boolean hasTest, boolean hasXval) {
      if(hasTest || true)_lambdaDevTest = new ArrayList<>();
      if(hasXval)_lambdaDevXval = new ArrayList<>();

    }

    public synchronized void addLambdaScore(int iter, int predictors, double lambda, double devRatioTrain, double devRatioTest, double devRatioXval) {
      _scoringTimes.add(System.currentTimeMillis());
      _lambdaIters.add(iter);
      _lambdas.add(lambda);
      _lambdaPredictors.add(predictors);
      _lambdaDevTrain.add(devRatioTrain);
      if(_lambdaDevTest != null)_lambdaDevTest.add(devRatioTest);
      if(_lambdaDevXval != null)_lambdaDevXval.add(devRatioXval);
    }
    public synchronized TwoDimTable to2dTable() {

      String[] cnames = new String[]{"timestamp", "duration", "iteration", "lambda", "predictors", "Explained Deviance (train)"};
      if(_lambdaDevTest != null)
        cnames = ArrayUtils.append(cnames,"Explained Deviance (test)");
      if(_lambdaDevXval != null)
        cnames = ArrayUtils.append(cnames,"Explained Deviance (xval)");
      String[] ctypes = new String[]{"string", "string", "int", "string","int", "double"};
      if(_lambdaDevTest != null)
        ctypes = ArrayUtils.append(ctypes,"double");
      if(_lambdaDevXval != null)
        ctypes = ArrayUtils.append(ctypes,"double");
      String[] cformats = new String[]{"%s", "%s", "%d","%s", "%d", "%.3f"};
      if(_lambdaDevTest != null)
        cformats = ArrayUtils.append(cformats,"%.3f");
      if(_lambdaDevXval != null)
        cformats = ArrayUtils.append(cformats,"%.3f");
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
        if(_lambdaDevXval != null && _lambdaDevXval.size() > i)
          res.set(i, col++, _lambdaDevXval.get(i));
      }
      return res;
    }
  }

  private transient ScoringHistory _sc;
  private transient LambdaSearchScoringHistory _lsc;

  long _t0 = System.currentTimeMillis();

  private transient double _iceptAdjust = 0;

  private transient GradientInfo _ginfo;
  private double _lmax;
  private transient long _nobs;
  private transient GLMModel _model;
  // special vecs made for irlsm
  private static final String _wName = "__glm_irlsm_wvec";
  private static final String _zName = "__glm_irlsm_zvec";
  private transient Vec _w;
  private transient Vec _z;
  // and for multinomial irlsm

  @Override
  public int nclasses() {
    if (_parms._family == Family.multinomial)
      return _nclass;
    if (_parms._family == Family.binomial)
      return 2;
    return 1;
  }

  private transient double[] _nullBeta;


  private double[] getNullPrediction() {
    double [] nb = getNullBeta();
    if(_parms._family != Family.multinomial)
      return new double[]{_parms.linkInv(nb[nb.length-1])};
    double [] res = new double[_nclass];
    if(_parms._intercept) {
      int N = _dinfo.fullN()+1;
      for (int i = 0; i < res.length; ++i)
        res[i] = Math.exp(nb[_dinfo.fullN() + i*N]);
    }
    return res;
  }
  private double[] getNullBeta() {
    if (_nullBeta == null) {
      if (_parms._family == Family.multinomial) {
        _nullBeta = MemoryManager.malloc8d((_dinfo.fullN() + 1) * nclasses());
        int N = _dinfo.fullN() + 1;
        for (int i = 0; i < nclasses(); ++i)
          _nullBeta[_dinfo.fullN() + i * N] = Math.log(_state._ymu[i]);
      } else {
        _nullBeta = MemoryManager.malloc8d(_dinfo.fullN() + 1);
        if (_parms._intercept)
          _nullBeta[_dinfo.fullN()] = new GLMModel.GLMWeightsFun(_parms).link(_state._ymu[0]);
        else
          _nullBeta[_dinfo.fullN()] = 0;
      }
    }
    return _nullBeta;
  }

  private transient GLMMetricBuilder _nullValidation;

  // static so I can make inner class mr task without sending whole glm over
  private static GLMMetricBuilder getNullValidation(final GLM glm) {
    return null;
  }

  protected boolean computePriorClassDistribution(){return _parms._family == Family.multinomial;}

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
      if (_parms._max_active_predictors == -1)
        _parms._max_active_predictors = _parms._solver == Solver.IRLSM ? 7000 : 100000000;
      if (_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
      _dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, _parms._standardize ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, _parms._missing_values_handling == MissingValuesHandling.Skip, false ,_parms._missing_values_handling == MissingValuesHandling.MeanImputation, hasWeightCol(), hasOffsetCol(), hasFoldCol(), _parms._interactions);

      if (_parms._max_iterations == -1) { // fill in default max iterations
        int numclasses = _parms._family == Family.multinomial?nclasses():1;
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
        int [] coffsets = null;

        YMUTask ymt = new YMUTask(_dinfo, _parms._family == Family.multinomial?nclasses():1, setWeights, skippingRows,true).doAll(_dinfo._adaptedFrame);
        if (ymt.wsum() == 0)
          throw new IllegalArgumentException("No rows left in the dataset after filtering out rows with missing values. Ignore columns with many NAs or impute your missing values prior to calling glm.");
        Log.info(LogMsg("using " + ymt.nobs() + " nobs out of " + _dinfo._adaptedFrame.numRows() + " total"));
        // if sparse data, need second pass to compute variance
        _nobs = ymt.nobs();
        if (_parms._obj_reg == -1)
          _parms._obj_reg = 1.0 / ymt.wsum();
        if(!_parms._stdOverride)
          _dinfo.updateWeightedSigmaAndMean(ymt.predictorSDs(), ymt.predictorMeans());
        _state._ymu = _parms._intercept?ymt._yMu:new double[]{_parms.linkInv(0)};
      } else {
        _nobs = _train.numRows();
        if (_parms._obj_reg == -1)
          _parms._obj_reg = 1.0 / _nobs;
        if (_parms._family == Family.multinomial) {
          _state._ymu = MemoryManager.malloc8d(_nclass);
          for (int i = 0; i < _state._ymu.length; ++i)
            _state._ymu[i] = _priorClassDist[i];
        } else
          _state._ymu = new double[]{_parms._intercept?_train.lastVec().mean():_parms.linkInv(0)};
      }
      BetaConstraint bc = (_parms._beta_constraints != null)?new BetaConstraint(_parms._beta_constraints.get()):new BetaConstraint();
      if((bc.hasBounds() || bc.hasProximalPenalty()) && _parms._compute_p_values)
        error("_compute_p_values","P-values can not be computed for constrained problems");
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
      String[] warns = _model.adaptTestForTrain(_valid, true, true);
      for (String s : warns) _job.warn(s);
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


    private void doCleanup() {
      try {
        _model.unlock(_job);
      } catch(Throwable t){
        // nada
      }
    }
    private transient Cholesky _chol;
    private transient L1Solver _lslvr;

    private double [] solveGram(Solver s, GLMIterationTask t) {
      return (s == Solver.COORDINATE_DESCENT)?COD_solve(t,_state._alpha,_state.lambda()):ADMM_solve(t._gram, t._xy);
    }

    private double[] ADMM_solve(Gram gram, double [] xy) {
      if(!_parms._intercept) {
        gram.dropIntercept();
        xy = Arrays.copyOf(xy, xy.length - 1);
      }
      gram.mul(_parms._obj_reg);
      ArrayUtils.mult(xy, _parms._obj_reg);
      if(_parms._remove_collinear_columns || _parms._compute_p_values) {
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
          xy = ArrayUtils.removeIds(xy,collinear_cols);
        }
        chol.solve(xy);
      } else {
        GramSolver slvr = new GramSolver(gram.clone(), xy.clone(), _parms._intercept, _state.l2pen(),_state.l1pen(), _state.activeBC()._betaGiven, _state.activeBC()._rho, _state.activeBC()._betaLB, _state.activeBC()._betaUB);
        _chol = slvr._chol;
        if(_state.l1pen() == 0 && !_state.activeBC().hasBounds()) {
          slvr.solve(xy);
        } else {
          xy = MemoryManager.malloc8d(xy.length);
          if(_state._u == null && _parms._family != Family.multinomial) _state._u = MemoryManager.malloc8d(_state.activeData().fullN()+1);
            (_lslvr = new ADMM.L1Solver(1e-4, 10000, _state._u)).solve(slvr, xy, _state.l1pen(), _parms._intercept, _state.activeBC()._betaLB, _state.activeBC()._betaUB);
        }
      }
      return _parms._intercept?xy:Arrays.copyOf(xy,xy.length+1);
    }

    private void fitIRLSM_multinomial(Solver s){
      assert _dinfo._responses == 3:"IRLSM for multinomial needs extra information encoded in additional reponses, expected 3 response vecs, got " + _dinfo._responses;
      double [] beta = _state.betaMultinomial();
      do {
        beta = beta.clone();
        for (int c = 0; c < _nclass; ++c) {
          if (_state.activeDataMultinomial(c).fullN() == 0) continue;
          _state.setActiveClass(c);
          LineSearchSolver ls = (_state.l1pen() == 0)
            ? new MoreThuente(_state.gslvrMultinomial(c), _state.betaMultinomial(c,beta), _state.ginfoMultinomial(c))
            : new SimpleBacktrackingLS(_state.gslvrMultinomial(c), _state.betaMultinomial(c,beta), _state.l1pen());
          GLMWeightsFun glmw = new GLMWeightsFun(_parms);
          long t1 = System.currentTimeMillis();
          new GLMMultinomialUpdate(_state.activeDataMultinomial(), _job._key, beta, c).doAll(_state.activeDataMultinomial()._adaptedFrame);
          long t2 = System.currentTimeMillis();
          GLMIterationTask t = new GLMTask.GLMIterationTask(_job._key, _state.activeDataMultinomial(c), glmw, ls.getX(), c).doAll(_state.activeDataMultinomial(c)._adaptedFrame);
          long t3 = System.currentTimeMillis();
          double[] betaCnd = solveGram(s,t);
          long t4 = System.currentTimeMillis();
          if (!ls.evaluate(ArrayUtils.subtract(betaCnd, ls.getX(), betaCnd))) {
            Log.info(LogMsg("Ls failed " + ls));
            continue;
          }
          long t5 = System.currentTimeMillis();
          _state.setBetaMultinomial(c, beta,ls.getX());
          // update multinomial
          Log.info(LogMsg("computed in " + (t2 - t1) + "+" + (t3 - t2) + "+" + (t4 - t3) + "+" + (t5 - t4) + "=" + (t5 - t1) + "ms, step = " + ls.step() + ((_lslvr != null) ? ", l1solver " + _lslvr : "")));
        }
        _state.setActiveClass(-1);
      } while(progress(beta,_state.gslvr().getGradient(beta)));
    }

    private void fitLSM(Solver s){
      GLMIterationTask t = new GLMTask.GLMIterationTask(_job._key, _state.activeData(), new GLMWeightsFun(_parms), null).doAll(_state.activeData()._adaptedFrame);
      int [] zeros = t._gram.dropZeroCols();
      t._xy = ArrayUtils.removeIds(t._xy,zeros);
      _state.removeCols(zeros);
      _state.updateState(solveGram(s,t), -1);
    }

    private void fitIRLSM(Solver s) {
      GLMWeightsFun glmw = new GLMWeightsFun(_parms);
      double [] betaCnd = _state.beta();
      LineSearchSolver ls = null;
      boolean firstIter = true;
      try {
        while (true) {
          long t1 = System.currentTimeMillis();
          GLMIterationTask t = new GLMTask.GLMIterationTask(_job._key, _state.activeData(), glmw, betaCnd).doAll(_state.activeData()._adaptedFrame);
          long t2 = System.currentTimeMillis();
          if (!_state._lsNeeded && (Double.isNaN(t._likelihood) || _state.objective(t._beta, t._likelihood) > _state.objective() + _parms._objective_epsilon)) {
            _state._lsNeeded = true;
          } else {
            if (!firstIter && !_state._lsNeeded && !progress(t._beta, t._likelihood))
              return;
            int [] zeros = t._gram.dropZeroCols();
            if(zeros.length > 0) {
              t._xy = ArrayUtils.removeIds(t._xy, zeros);
              t._beta = ArrayUtils.removeIds(t._beta, zeros);
              _state.removeCols(zeros);
            }
            betaCnd = solveGram(s,t);
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
      double lambda = _state.lambda();
      final double l1pen = _state.l1pen();
      final double l2pen = _state.l2pen();
      GLMGradientSolver gslvr = _state.gslvr();
      GLMWeightsFun glmw = new GLMWeightsFun(_parms);
      if (_parms._family == Family.multinomial) {
        beta = MemoryManager.malloc8d((_state.activeData().fullN() + 1) * _nclass);
        int P = _state.activeData().fullN() + 1;
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
        if(_state._iter == 0)
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
              beta[_state.activeData()._catOffsets[i]+j] = ADMM.shrinkage(stupdate._temp[j] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                / (denums[_state.activeData()._catOffsets[i]+j] / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
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

              beta[i+_state.activeData().numStart()] = ADMM.shrinkage(stupdate._temp[0] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                / (denums[i+_state.activeData().numStart()] / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
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
              beta[_state.activeData().numStart()] = ADMM.shrinkage(stupdate._temp[0] / wsumu, _parms._lambda[_lambdaId] * _parms._alpha[0])
                / (denums[_state.activeData().numStart()] / wsumu + _parms._lambda[_lambdaId] * (1 - _parms._alpha[0]));
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
          else if(_parms._family == Family.gaussian && _parms._link == Link.identity)
            fitLSM(solver);
          else
            fitIRLSM(solver);
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
        double [] gInvDiag = _chol.getInvDiag();
        for(int i = 0; i < zvalues.length; ++i)
          zvalues[i] = beta[i]/Math.sqrt(_parms._obj_reg*gInvDiag[i]*se);
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
      _model.addSubmodel(_state.beta(),lambda,_state._iter);
      _state.setLambda(lambda);
      checkMemoryFootPrint(_state.activeData());
      do {
        if(_parms._family == Family.multinomial)
          for(int c = 0; c < _nclass; ++c)
            Log.info(LogMsg("Class " + c + " got " + _state.activeDataMultinomial(c).fullN() + " active columns out of " + _state._dinfo.fullN() + " total"));
        else
          Log.info(LogMsg("Got " + _state.activeData().fullN() + " active columns out of " + _state._dinfo.fullN() + " total"));
        fitModel();
      } while(!_state.checkKKTs());
      Log.info(LogMsg("solution has " + ArrayUtils.countNonzeros(_state.beta()) + " nonzeros"));
      if(_parms._lambda_search) {  // need train and test deviance, only "the best" submodel will be fully scored
        double trainDev = _state.deviance();
        double testDev = -1;
        if(_validDinfo != null){
          testDev = _parms._family == Family.multinomial
              ? new GLMResDevTaskMultinomial(_job._key, _validDinfo, _dinfo.denormalizeBeta(_state.beta()), _nclass).doAll(_validDinfo._adaptedFrame)._likelihood * 2
              : new GLMResDevTask(_job._key, _validDinfo, _parms, _dinfo.denormalizeBeta(_state.beta())).doAll(_validDinfo._adaptedFrame)._resDev;
        }
        Log.info(LogMsg("train deviance = " + trainDev + ", test deviance = " + testDev));
        double xvalDev = _xval_test_deviances == null?-1:_xval_test_deviances[i];
        _lsc.addLambdaScore(_state._iter,ArrayUtils.countNonzeros(_state.beta()), _state.lambda(),1 - trainDev/_nullDevTrain, 1.0 - testDev/_nullDevTest, xvalDev == -1?-1:1.0 - xvalDev/_nullDevTrain);
        _model.update(_state.beta(), trainDev, testDev, _state._iter);
      } else // model is gonna be scored subsequently anyways
        _model.update(_state.beta(), -1, -1, _state._iter);
      return _model._output.getSubmodel(lambda);
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
        _nullDevTrain =  _parms._family == Family.multinomial
          ?new GLMResDevTaskMultinomial(_job._key,_state._dinfo,getNullBeta(), _nclass).doAll(_state._dinfo._adaptedFrame)._likelihood*2
          :new GLMResDevTask(_job._key, _state._dinfo, _parms, getNullBeta()).doAll(_state._dinfo._adaptedFrame)._resDev;
        if(_validDinfo != null)
          _nullDevTest = _parms._family == Family.multinomial
            ?new GLMResDevTaskMultinomial(_job._key,_validDinfo,getNullBeta(), _nclass).doAll(_validDinfo._adaptedFrame)._likelihood*2
            :new GLMResDevTask(_job._key, _validDinfo, _parms, getNullBeta()).doAll(_validDinfo._adaptedFrame)._resDev;
        _workPerIteration = WORK_TOTAL/_parms._nlambdas;
      } else
        _workPerIteration = 1 + (WORK_TOTAL/_parms._max_iterations);

      if(_parms._family == Family.multinomial && _parms._solver != Solver.L_BFGS && (_parms._solver != Solver.AUTO || defaultSolver() != Solver.L_BFGS) ) {
        double [] nb = getNullBeta();
        double maxRow = ArrayUtils.maxValue(nb);
        double sumExp = 0;
        int P = _dinfo.fullN();
        int N = _dinfo.fullN()+1;
        for(int i = 1; i < _nclass; ++i)
          sumExp += Math.exp(nb[i*N + P] - maxRow);
        _dinfo.addResponse(new String[]{"__glm_sumExp", "__glm_maxRow"}, _dinfo._adaptedFrame.anyVec().makeDoubles(2, new double[]{sumExp,maxRow}));
      }
      double oldDevTrain = _nullDevTrain;
      double oldDevTest = _nullDevTest;
      double [] devHistoryTrain = new double[5];
      double [] devHistoryTest = new double[5];

      if(!_parms._lambda_search)
        updateProgress(false);
      // lambda search loop
      for (int i = 0; i < _parms._lambda.length; ++i) {  // lambda search
        Submodel sm = computeSubmodel(i,_parms._lambda[i]);
        double trainDev = sm.devianceTrain;
        double testDev = sm.devianceTest;
        devHistoryTest[i % devHistoryTest.length] = (oldDevTest - testDev)/oldDevTest;
        oldDevTest = testDev;
        devHistoryTrain[i % devHistoryTrain.length] = (oldDevTrain - trainDev)/oldDevTrain;
        oldDevTrain = trainDev;
        if(_parms._early_stopping && _state._iter >= devHistoryTrain.length) {
          double s = ArrayUtils.maxValue(devHistoryTrain);
          if(s < 1e-4) {
            Log.info(LogMsg("converged at lambda[" + i + "] = " + _parms._lambda[i] + ", improvement on train = " + s));
            break; // started overfitting
          }
          if(_validDinfo != null) {
            s = ArrayUtils.maxValue(devHistoryTrain);
            if(s < 1e-4) {
              Log.info(LogMsg("converged at lambda[" + i + "] = " + _parms._lambda[i] + ", improvement on test = " + s));
              break; // started overfitting
            }
          }
        }
        if(_parms._lambda_search && (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval))
          scoreAndUpdateModel(); // update partial results
        _job.update(_workPerIteration,"iter=" + _state._iter + " lmb=" + lambdaFormatter.format(_state.lambda()) + "exp.dev.ratio trn/tst= " + devFormatter.format(1 - trainDev/_nullDevTrain) + "/" + devFormatter.format(1.0 - testDev/_nullDevTest) + " P=" + ArrayUtils.countNonzeros(_state.beta()));
      }
      if(_state._iter >= _parms._max_iterations)
        _job.warn("Reached maximum number of iterations " + _parms._max_iterations + "!");
      _model._output.pickBestModel();
      scoreAndUpdateModel();
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

    private double betaDiff(double [] b1, double [] b2) {
      double res = Math.abs(b1[0] - b2[0]);
      for(int i  = 0; i < b1.length; ++i) {
        double diff = b1[i] - b2[i];
        if(diff > res) res = diff;
        else if(-diff > res) res = -diff;
      }
      return res;
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
    if(_state.activeData().fullN() >= 5000) // cutoff has to be somewhere
      s = Solver.L_BFGS;
    else if(_parms._lambda_search && _parms._alpha[0] > 0) { // lambda search prefers coordinate descent
      // l1 lambda search is better with coordinate descent!
      s = Solver.COORDINATE_DESCENT;
    } else if(_state.activeBC().hasBounds()) {
      s = Solver.COORDINATE_DESCENT;
    } else if(_parms._family == Family.multinomial && _parms._alpha[0] == 0)
      s = Solver.L_BFGS; // multinomial does better with lbfgs
    else
      Log.info(LogMsg("picked solver " + s));
    _parms._solver = s;
    return s;
  }

  private double currentLambda() {
    return _parms._lambda[_lambdaId];
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
  private static double [] doSparseUpdateCD(double [] grads, double [] ary, int[] ids, double diff , int variable_min, int variable_max) {
    for(int i = 0; i < ids.length; ++i)
      grads[ids[i]] += diff * ary[i];
    return grads;
  }
  public double [] COD_solve(GLMIterationTask gt, double alpha, double lambda) {
    gt._gram.mul(_parms._obj_reg);
    ArrayUtils.mult(gt._xy,_parms._obj_reg);
    double wsumInv = 1.0/(gt.wsum*_parms._obj_reg);
    double l1pen = lambda * alpha;
    double l2pen = lambda*(1-alpha);
    double [][] xx = gt._gram.getXX();
    double [] diagInv = MemoryManager.malloc8d(xx.length);
    for(int i = 0; i < diagInv.length; ++i)
      diagInv[i] = 1.0/(xx[i][i] + l2pen);
    int [][] nzs = new int[_state.activeData().numStart()][];
    if(nzs.length > 1000) {
      final int [] nzs_ary = new int[xx.length];
      for (int i = 0; i < nzs.length; ++i) {
        double[] x = xx[i].clone();
        int k = 0;
        for (int j = 0; j < x.length; ++j) {
          if (i != j && x[j] != 0) {
            x[k] = x[j];
            nzs_ary[k++] = j;
          }
        }
        if (k < (nzs_ary.length >> 3)) {
          nzs[i] = Arrays.copyOf(nzs_ary, k);
          xx[i] = Arrays.copyOf(x,k);
        }
      }
    }
    double [] grads = new double [gt._xy.length];
    double [] beta = _state.beta().clone();
    for(int i = 0; i < grads.length; ++i) {
      double ip = 0;
      if(i < nzs.length && nzs[i] != null) {
        int [] ids = nzs[i];
        double [] x = xx[i];
        for(int j = 0; j < nzs[i].length; ++j)
          ip += x[j]*beta[ids[j]];
        grads[i] =  gt._xy[i] - ip;
      } else {
        grads[i] =  gt._xy[i] - ArrayUtils.innerProduct(xx[i], beta) + xx[i][i] * beta[i];
      }
    }
    int iter1 = 0;
    int P = gt._xy.length - 1;
    final BetaConstraint bc = _state.activeBC();
    DataInfo activeData = _state.activeData();
    // CD loop
    while (iter1++ < 1000 /*Math.max(P,500)*/) {
      double bdiffPos = 0;
      double bdiffNeg = 0;
      for (int i = 0; i < activeData._cats; ++i) {
        for(int j = activeData._catOffsets[i]; j < activeData._catOffsets[i+1]; ++j) { // can do in parallel
          double b = bc.applyBounds(ADMM.shrinkage(grads[j], l1pen) * diagInv[j],j);
          double bd = beta[j] - b;
          bdiffPos = bd > bdiffPos?bd:bdiffPos;
          bdiffNeg = bd < bdiffNeg?bd:bdiffNeg;
          if(nzs[j] == null)
            doUpdateCD(grads, xx[j], bd, activeData._catOffsets[i], activeData._catOffsets[i + 1]);
          else
            doSparseUpdateCD(grads, xx[j], nzs[j], bd, activeData._catOffsets[i], activeData._catOffsets[i + 1]);
          beta[j] = b;
        }
      }
      int numStart = activeData.numStart();
      for (int i = numStart; i < P; ++i) {
        double b = bc.applyBounds(ADMM.shrinkage(grads[i], l1pen) * diagInv[i],i);
        double bd = beta[i] - b;
        bdiffPos = bd > bdiffPos?bd:bdiffPos;
        bdiffNeg = bd < bdiffNeg?bd:bdiffNeg;
        doUpdateCD(grads, xx[i], bd, i,i+1);
        beta[i] = b;
      }
      // intercept
      if(_parms._intercept) {
        double b = bc.applyBounds(grads[P] * wsumInv,P);
        double bd = beta[P] - b;
        doUpdateCD(grads, xx[P], bd, P, P + 1);
        bdiffPos = bd > bdiffPos ? bd : bdiffPos;
        bdiffNeg = bd < bdiffNeg ? bd : bdiffNeg;
        beta[P] = b;
      }
      if (-1e-4 < bdiffNeg && bdiffPos < 1e-4)
        break;
    }
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
      computeCholesky(gram, rhos, lmax * 1e-8);
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
      int icptCol = xy.length - 1;
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
      computeCholesky(gram, rhos, 1e-5);
      _rho = rhos;
      _xy = xy;
    }

    private void computeCholesky(Gram gram, double[] rhos, double rhoAdd) {
      gram.addDiag(rhos);
      _chol = gram.cholesky(null, true, null);
      if (!_chol.isSPD()) { // make sure rho is big enough
        gram.addDiag(ArrayUtils.mult(rhos, -1));
        ArrayUtils.mult(rhos, -1);
        for (int i = 0; i < rhos.length; ++i)
          rhos[i] += rhoAdd;//1e-5;
        Log.info("Got NonSPD matrix with original rho, re-computing with rho = " + rhos[0]);
        _gram.addDiag(rhos);
        _chol = gram.cholesky(null, true, null);
        int cnt = 0;
        while (!_chol.isSPD() && cnt++ < 5) {
          gram.addDiag(ArrayUtils.mult(rhos, -1));
          ArrayUtils.mult(rhos, -1);
          for (int i = 0; i < rhos.length; ++i)
            rhos[i] *= 100;
          Log.warn("Still NonSPD matrix, re-computing with rho = " + rhos[0]);
          _gram.addDiag(rhos);
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

    @Override
    public GLMGradientInfo getGradient(double[] beta) {
      if (_parms._family == Family.multinomial) {
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
        GLMMultinomialGradientTask gt = new GLMMultinomialGradientTask(_job,_dinfo, _l2pen, _betaMultinomial, _parms._obj_reg).doAll(_dinfo._adaptedFrame);
        double l2pen = 0;
        for (double[] b : _betaMultinomial)
          l2pen += ArrayUtils.l2norm2(b, _dinfo._intercept);
        return new GLMGradientInfo(gt._likelihood, gt._likelihood * _parms._obj_reg + .5 * _l2pen * l2pen, gt.gradient());
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

  /**
   * GLM implementation of N-fold cross-validation.
   * GLM needs its own implementation when running in lambda search, it needs the following extra steps:
   *   1. compute lambdas of the main model (we want to be comparing models at same lambda values)
   *   2. after modesl are built, pick global best lambda and compute a model for it in each fold.
   * @return Cross-validation Job
   * (builds N+1 models, all have train+validation metrics, the main model has N-fold cross-validated validation metrics)
   */
  @Override
  public void computeCrossValidation() {
    init(true);
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
    // init computes global list of lambdas
    // 2. is handled in modifyParams...
    super.computeCrossValidation();
  }
}
