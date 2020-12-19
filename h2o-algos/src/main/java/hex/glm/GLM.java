package hex.glm;

import hex.*;
import hex.glm.GLMModel.GLMOutput;
import hex.glm.GLMModel.GLMParameters.*;
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
import hex.svd.SVD;
import hex.svd.SVDModel;
import hex.svd.SVDModel.SVDParameters;
import hex.util.CheckpointUtils;
import hex.util.LinearAlgebraUtils;
import hex.util.LinearAlgebraUtils.BMulTask;
import hex.util.LinearAlgebraUtils.FindMaxIndex;
import jsr166y.CountedCompleter;
import org.joda.time.format.DateTimeFormat;
import org.joda.time.format.DateTimeFormatter;
import water.*;
import water.exceptions.H2OModelBuilderIllegalArgumentException;
import water.fvec.Frame;
import water.fvec.InteractionWrappedVec;
import water.fvec.Vec;
import water.parser.BufferedString;
import water.rapids.Rapids;
import water.rapids.Val;
import water.util.*;

import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;

import static hex.ModelMetrics.calcVarImp;
import static hex.glm.GLMModel.GLMParameters;
import static hex.glm.GLMModel.GLMParameters.CHECKPOINT_NON_MODIFIABLE_FIELDS;
import static hex.glm.GLMUtils.*;
import static water.fvec.Vec.T_STR;

/**
 * Created by tomasnykodym on 8/27/14.
 *
 * Generalized linear model implementation.
 */
public class GLM extends ModelBuilder<GLMModel,GLMParameters,GLMOutput> {
  static NumberFormat lambdaFormatter = new DecimalFormat(".##E0");
  static NumberFormat devFormatter = new DecimalFormat(".##");
  private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormat.forPattern("yyyy-MM-dd HH:mm:ss");
  
  public static final int SCORING_INTERVAL_MSEC = 15000; // scoreAndUpdateModel every minute unless score every iteration is set
  public int[] _randC;  // contains categorical column levels for random columns for HGLM
  public String _generatedWeights = null;
  public String[] _randCoeffNames = null;
  public String[] _randomColNames = null;
  public double[][][] _penaltyMatrix = null;
  public String[][] _gamColnames = null;
  public int[][] _gamColIndices = null; // corresponding column indices in dataInfo
  public static int _totalBetaLen;
  private boolean _earlyStopEnabled = false;
  private boolean _checkPointFirstIter = false;  // indicate first iteration for checkpoint model

  public GLM(boolean startup_once){super(new GLMParameters(),startup_once);}
  public GLM(GLMModel.GLMParameters parms) {
    super(parms);
    init(false);
  }

  /***
   * This constructor is only called by GAM when it is trying to build a GAM model using GLM.  
   * 
   * Internal function, DO NOT USE.
   */
  public GLM(GLMModel.GLMParameters parms, double[][][] penaltyMatrix, String[][] gamColnames) {
    super(parms);
    init(false);
    _penaltyMatrix = penaltyMatrix;
    _gamColnames = gamColnames;
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
  private int _bestCVSubmodel;  // best submodel index found during cv
  private boolean _doInit = true;  // flag setting whether or not to run init
  private double [] _xval_deviances;
  private double [] _xval_sd;

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
    if (error_count() > 0)
      throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
    super.computeCrossValidation();
  }

  /**
   * If run with lambda search, we need to take extra action performed after cross-val models are built.
   * Each of the folds have been computed with ots own private validation dataset and it performed early stopping based on it.
   * => We need to:
   *   1. compute cross-validated lambda estimate
   *   2. set the lambda estimate to all n-folds models (might require extra model fitting if the particular model
   *   stopped too early!)
   *   3. compute cross-validated scoring history (cross-validated deviance standard error per lambda)
   *   4. unlock the n-folds models (they are changed here, so the unlocking happens here)
   */
  @Override
  public void cv_computeAndSetOptimalParameters(ModelBuilder[] cvModelBuilders) {
    if(_parms._max_runtime_secs != 0) _parms._max_runtime_secs = 0;
      _xval_deviances = new double[_parms._lambda.length*_parms._alpha.length];
      _xval_sd = new double [_parms._lambda.length*_parms._alpha.length];
      double bestTestDev = Double.POSITIVE_INFINITY;
      int lmin_max = 0;
      for (int i = 0; i < cvModelBuilders.length; ++i) {  // find the highest best_submodel_idx we need to go through
        GLM g = (GLM) cvModelBuilders[i];
        lmin_max = Math.max(lmin_max,g._model._output._selected_submodel_idx);
      }
      int lidx = 0; // index into submodel
      int bestId = 0;   // submodel indedx with best Deviance from xval
      int cnt = 0;
      for (; lidx < lmin_max; ++lidx) { // search through submodel with same lambda and alpha values
        double testDev = 0;
        double testDevSq = 0;
        for (int i = 0; i < cvModelBuilders.length; ++i) {  // run cv for each lambda value
          GLM g = (GLM) cvModelBuilders[i];
          if (g._model._output._submodels[lidx] != null) {
            double lambda = g._model._output._submodels[lidx].lambda_value;
            g._driver.computeSubmodel(lidx, lambda, Double.NaN, Double.NaN);
            testDev += g._model._output._submodels[lidx].devianceValid;
            testDevSq += g._model._output._submodels[lidx].devianceValid * g._model._output._submodels[lidx].devianceValid;
          }
        }
        double testDevAvg = testDev / cvModelBuilders.length; // average testDevAvg for fixed submodel index
        double testDevSE = testDevSq - testDevAvg*testDev;
        _xval_sd[lidx] = Math.sqrt(testDevSE/((cvModelBuilders.length-1)*cvModelBuilders.length));
        _xval_deviances[lidx] = testDevAvg;
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
      _xval_deviances = Arrays.copyOf(_xval_deviances, lmin_max+1);
      _xval_sd = Arrays.copyOf(_xval_sd, lmin_max+1);
      for (int i = 0; i < cvModelBuilders.length; ++i) {
        GLM g = (GLM) cvModelBuilders[i];
        g._model._output.setSubmodelIdx(bestId);
      }
      double bestDev = _xval_deviances[bestId];
      double bestDev1se = bestDev + _xval_sd[bestId];
      int bestId1se = bestId;
      while(bestId1se > 0 && _xval_deviances[bestId1se-1] <= bestDev1se)
        --bestId1se;
      _lambdaCVEstimate = ((GLM) cvModelBuilders[0])._model. _output._submodels[bestId].lambda_value;
      _bestCVSubmodel = bestId;
      _model._output._lambda_1se = bestId1se; // submodel ide with bestDev+one sigma
      _model._output._selected_submodel_idx = bestId; // set best submodel id here
    for (int i = 0; i < cvModelBuilders.length; ++i) {
      GLM g = (GLM) cvModelBuilders[i];
      GLMModel gm = g._model;
      gm.write_lock(_job);
      gm.update(_job);
      gm.unlock(_job);
    }
    _doInit = false;
  }

  protected void checkMemoryFootPrint(DataInfo activeData) {
    if (_parms._solver == Solver.IRLSM || _parms._solver == Solver.COORDINATE_DESCENT) {
      int p = activeData.fullN();
      HeartBeat hb = H2O.SELF._heartbeat;
      long mem_usage = (long) (hb._cpus_allowed * (p * p + activeData.largestCat()) * 8/*doubles*/ * (1 + .5 * Math.log((double) _train.lastVec().nChunks()) / Math.log(2.))); //one gram per core
      long max_mem = hb.get_free_mem();
      if (_parms._HGLM) { // add check to check memories used by arrays.
        int expandedRandColValues = ArrayUtils.sum(_randC);
        mem_usage = expandedRandColValues*expandedRandColValues*5+(_nobs+expandedRandColValues)*5;  // rough estimate
      }
      if (mem_usage > max_mem) {
        String msg = "Gram matrices (one per thread) won't fit in the driver node's memory ("
          + PrettyPrint.bytes(mem_usage) + " > " + PrettyPrint.bytes(max_mem)
          + ") - try reducing the number of columns and/or the number of categorical factors (or switch to the L-BFGS solver).";
        error("_train", msg);
      }
    }
  }

  DataInfo _dinfo;

  private transient DataInfo _validDinfo;
  // time per iteration in ms

  static class ScoringHistory {
    private ArrayList<Integer> _scoringIters = new ArrayList<>();
    private ArrayList<Long> _scoringTimes = new ArrayList<>();
    private ArrayList<Double> _likelihoods = new ArrayList<>();
    private ArrayList<Double> _objectives = new ArrayList<>();
    private ArrayList<Double> _convergence = new ArrayList<>(); // HGLM: ratio of sum(eta0-eta.i)^2/sum(eta.i^2)
    private ArrayList<Double> _sumEtaiSquare = new ArrayList<>();  // HGLM: sum(eta.i^2)
    
    public ArrayList<Integer> getScoringIters() { return _scoringIters;}
    public ArrayList<Long> getScoringTimes() { return _scoringTimes;}
    public ArrayList<Double> getLikelihoods() { return _likelihoods;}
    public ArrayList<Double> getObjectives() { return _objectives;}
    
    public synchronized void addIterationScore(int iter, double likelihood, double obj) {
      if (_scoringIters.size() > 0 && _scoringIters.get(_scoringIters.size() - 1) == iter)
        return; // do not record twice, happens for the last iteration, need to record scoring history in checkKKTs because of gaussian fam.
      _scoringIters.add(iter);
      _scoringTimes.add(System.currentTimeMillis());
      _likelihoods.add(likelihood);
      _objectives.add(obj);
    }

    public synchronized void addIterationScore(int iter, double[] sumEtaInfo) {
      if (_scoringIters.size() > 0 && _scoringIters.get(_scoringIters.size() - 1) == iter)
        return; // do not record twice, happens for the last iteration, need to record scoring history in checkKKTs because of gaussian fam.
      _scoringIters.add(iter);
      _scoringTimes.add(System.currentTimeMillis());
      _sumEtaiSquare.add(sumEtaInfo[0]);
      _convergence.add(sumEtaInfo[0]/sumEtaInfo[1]);
    }

    public synchronized TwoDimTable to2dTable() {
      String[] cnames = new String[]{"timestamp", "duration", "iterations", "negative_log_likelihood", "objective"};
      String[] ctypes = new String[]{"string", "string", "int", "double", "double"};
      String[] cformats = new String[]{"%s", "%s", "%d", "%.5f", "%.5f"};
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_scoringIters.size()], cnames, ctypes, cformats, "");
      for (int i = 0; i < _scoringIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, DATE_TIME_FORMATTER.print(_scoringTimes.get(i)));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
        res.set(i, col++, _scoringIters.get(i));
        res.set(i, col++, _likelihoods.get(i));
        res.set(i, col++, _objectives.get(i));
      }
      return res;
    }
    
    public synchronized TwoDimTable to2dTableHGLM() {
      String[] cnames = new String[]{"timestamp", "duration", "iterations", "sum(etai-eta0)^2", "convergence"};
      String[] ctypes = new String[]{"string", "string", "int", "double", "double"};
      String[] cformats = new String[]{"%s", "%s", "%d", "%.5f", "%.5f"};
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_scoringIters.size()], cnames, ctypes, cformats, "");
      for (int i = 0; i < _scoringIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, DATE_TIME_FORMATTER.print(_scoringTimes.get(i)));
        res.set(i, col++, PrettyPrint.msecs(_scoringTimes.get(i) - _scoringTimes.get(0), true));
        res.set(i, col++, _scoringIters.get(i));
        res.set(i, col++, _sumEtaiSquare.get(i));
        res.set(i, col++, _convergence.get(i));
      }
      return res;
    }

    void restoreFromCheckpoint(TwoDimTable sHist, int[] colIndices, boolean hglm) {
      int numRows = sHist.getRowDim();
      for (int rowInd = 0; rowInd < numRows; rowInd++) {  // if lambda_search is enabled, _sc is not updated
        _scoringIters.add((Integer) sHist.get(rowInd, colIndices[0]));
        _scoringTimes.add(DATE_TIME_FORMATTER.parseMillis((String) sHist.get(rowInd, colIndices[1])));
        _likelihoods.add((Double) sHist.get(rowInd, colIndices[2]));
        _objectives.add((Double) sHist.get(rowInd, colIndices[3]));
        if (hglm) {  // for HGLM family
          _convergence.add((Double) sHist.get(rowInd, colIndices[4]));
          _sumEtaiSquare.add((Double) sHist.get(rowInd, colIndices[5]));
        }
      }
    }
    
  }

  static class LambdaSearchScoringHistory {
    ArrayList<Long> _scoringTimes = new ArrayList<>();
    private ArrayList<Double> _lambdas = new ArrayList<>();
    private ArrayList<Integer> _lambdaIters = new ArrayList<>();
    private ArrayList<Integer> _lambdaPredictors = new ArrayList<>();
    private ArrayList<Double> _lambdaDevTrain = new ArrayList<>();
    private ArrayList<Double> _lambdaDevTest;
    private ArrayList<Double> _lambdaDevXval;
    private ArrayList<Double> _lambdaDevXvalSE;
    private ArrayList<Double> _alphas = new ArrayList<>();

    public LambdaSearchScoringHistory(boolean hasTest, boolean hasXval) {
      if(hasTest || true)_lambdaDevTest = new ArrayList<>();
      if(hasXval){
        _lambdaDevXval = new ArrayList<>();
        _lambdaDevXvalSE = new ArrayList<>();
      }
    }

    public ArrayList<Integer> getScoringIters() { return _lambdaIters;}
    public ArrayList<Long> getScoringTimes() { return _scoringTimes;}
    public ArrayList<Double> getLambdas() { return _lambdas;}
    public ArrayList<Double> getAlphas() { return _alphas;}
    public ArrayList<Double> getDevTrain() { return _lambdaDevTrain;}
    public ArrayList<Double> getDevTest() { return _lambdaDevTest;}
    public ArrayList<Integer> getPredictors() { return _lambdaPredictors;}
    

    public synchronized void addLambdaScore(int iter, int predictors, double lambda, double devRatioTrain, double devRatioTest, double devRatioXval, double devRatoioXvalSE, double alpha) {
      _scoringTimes.add(System.currentTimeMillis());
      _lambdaIters.add(iter);
      _alphas.add(alpha);
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
      cnames = ArrayUtils.append(cnames, "alpha");
      ctypes = ArrayUtils.append(ctypes, "double");
      cformats = ArrayUtils.append(cformats, "%.6f");
      TwoDimTable res = new TwoDimTable("Scoring History", "", new String[_lambdaIters.size()], cnames, ctypes, cformats, "");
      for (int i = 0; i < _lambdaIters.size(); ++i) {
        int col = 0;
        res.set(i, col++, DATE_TIME_FORMATTER.print(_scoringTimes.get(i)));
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
        res.set(i, col++, _alphas.get(i));
      }
      return res;
    }

    void restoreFromCheckpoint(TwoDimTable sHist, int[] colIndices) {
      int numRows = sHist.getRowDim();
      for (int rowInd = 0; rowInd < numRows; rowInd++) {
        _scoringTimes.add(DATE_TIME_FORMATTER.parseMillis((String) sHist.get(rowInd, colIndices[1])));
        _lambdaIters.add((int) sHist.get(rowInd, colIndices[0]));
        _lambdas.add(Double.valueOf((String) sHist.get(rowInd, colIndices[2])));
        _alphas.add((Double) sHist.get(rowInd, colIndices[6]));
        _lambdaPredictors.add((int) sHist.get(rowInd, colIndices[3]));
        _lambdaDevTrain.add((double) sHist.get(rowInd, colIndices[4]));
        _lambdaDevTest.add((double) sHist.get(rowInd, colIndices[5]));
      }
    }
  }

  private transient ScoringHistory _scoringHistory;
  private transient LambdaSearchScoringHistory _lambdaSearchScoringHistory;

  long _t0 = System.currentTimeMillis();

  private transient double _iceptAdjust = 0;

  private double _lmax;
  private double _gmax; // gradient max without dividing by math.max(1e-2, _parms._alpha[0])
  private transient long _nobs;
  private transient GLMModel _model;
  private boolean _earlyStop = false;  // set by earlyStopping
  private GLMGradientInfo _ginfoStart;
  private double _betaDiffStart;
  private double[] _betaStart;
  
  @Override
  public int nclasses() {
    if (_parms._family == Family.multinomial || _parms._family == Family.ordinal || _parms._family == Family.AUTO)
      return _nclass;
    if (Family.binomial == _parms._family || Family.quasibinomial == _parms._family
            || Family.fractionalbinomial == _parms._family)
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

  protected boolean computePriorClassDistribution() {
    return (_parms._family == Family.multinomial) || (_parms._family == Family.ordinal) || (_parms._family == Family.AUTO && nclasses() > 2);
  }

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
      if ((Solver.GRADIENT_DESCENT_LH.equals(_parms._solver) || Solver.GRADIENT_DESCENT_LH.equals(_parms._solver)) 
              && !Family.ordinal.equals(_parms._family))
        error("_solver", "Solvers GRADIENT_DESCENT_LH and GRADIENT_DESCENT_SQERR are only " +
                "supported for ordinal regression.  Do not choose them unless you specify your family to be ordinal");
      switch (_parms._family) {
        case AUTO:
          if (nclasses() == 1 & _parms._link != Link.family_default && _parms._link != Link.identity 
                  && _parms._link != Link.log && _parms._link != Link.inverse) {
            error("_family", H2O.technote(2, "AUTO for undelying response requires the link to be family_default, identity, log or inverse."));
          } else if (nclasses() == 2 & _parms._link != Link.family_default && _parms._link != Link.logit) {
            error("_family", H2O.technote(2, "AUTO for undelying response requires the link to be family_default or logit."));
          } else if (nclasses() > 2 & _parms._link != Link.family_default & _parms._link != Link.multinomial) {
            error("_family", H2O.technote(2, "AUTO for undelying response requires the link to be family_default or multinomial."));
          }
          break;
        case binomial:
          if (!_response.isBinary() && _nclass != 2)
            error("_family", H2O.technote(2, "Binomial requires the response to be a 2-class categorical or a binary column (0/1)"));
          break;
        case multinomial:
          if (_nclass <= 2)
            error("_family", H2O.technote(2, "Multinomial requires a categorical response with at least 3 levels (for 2 class problem use family=binomial."));
          break;
        case poisson:
        case negativebinomial:  
          if (_nclass != 1) error("_family", "Poisson and Negative Binomial require the response" +
                  " to be numeric.");
          if (_response.min() < 0)
            error("_family", "Poisson and Negative Binomial require response >= 0");
          if (!_response.isInt())
            warn("_family", "Poisson and Negative Binomial expect non-negative integer response," +
                    " got floats.");
          if (Family.negativebinomial.equals(_parms._family))
            if (_parms._theta <= 0 || _parms._theta > 1)
              error("_family", "Illegal Negative Binomial theta value.  Valid theta values be > 0" +
                      " and <= 1.");
            else
              _parms._invTheta = 1 / _parms._theta;
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
        case fractionalbinomial:
          final Vec resp = (train()).vec(_parms._response_column);
          if ((resp.min() < 0) || (resp.max() > 1)) {
            error("response",
                    String.format("Response '%s' must be between 0 and 1 for fractional_binomial family. Min: %f, Max: %f",
                            _parms._response_column, resp.min(), resp.max()));
          }
          break;
        default:
          error("_family", "Invalid distribution: " + _parms._distribution);
      }
    }
    if ((_parms._plug_values != null) && (_parms.missingValuesHandling() != MissingValuesHandling.PlugValues)) {
      error("_missing_values_handling", "When plug values are provided - Missing Values Handling needs to be explicitly set to PlugValues.");
    }
    if (_parms._plug_values == null && _parms.missingValuesHandling() == MissingValuesHandling.PlugValues) {
      error("_missing_values_handling", "No plug values frame provided for Missing Values Handling = PlugValues.");
    }
    if (_parms._HGLM) {
      for (int randInx:_parms._random_columns) {
        if (!_parms.train().vec(randInx).isCategorical()) {
          error("HGLM random_columns", "Must contain categorical columns.");
        }
      }
    }
    if (expensive) {
      if (error_count() > 0) return;
      if (_parms._lambda_search && (_parms._stopping_rounds > 0)) {
        error("early stop", " cannot run when lambda_search=True.  Lambda_search has its own " +
                "early-stopping mechanism");
      }
      if (!_parms._lambda_search && (_parms._stopping_rounds > 0))  // early stop is on!
        _earlyStopEnabled = true;
      if (_parms._alpha == null)
        _parms._alpha = new double[]{_parms._solver == Solver.L_BFGS ? 0 : .5};
      if (_parms._lambda_search  &&_parms._nlambdas == -1)
          _parms._nlambdas = _parms._alpha[0] == 0?30:100; // fewer lambdas needed for ridge
      _lambdaSearchScoringHistory = new LambdaSearchScoringHistory(_parms._valid != null,_parms._nfolds > 1);
      _scoringHistory = new ScoringHistory();
      _train.bulkRollups(); // make sure we have all the rollups computed in parallel
      _t0 = System.currentTimeMillis();
      if ((_parms._lambda_search || !_parms._intercept || _parms._lambda == null || _parms._lambda[0] > 0) && !_parms._HGLM)
        _parms._use_all_factor_levels = true;
      if (_parms._family == Family.AUTO) {
        if (_nclass == 1) {
          _parms._family = Family.gaussian;
        } else if (_nclass == 2) {
          _parms._family = Family.binomial;
        } else {
          _parms._family = Family.multinomial;
        }
      }
      if (_parms._link == Link.family_default)
        _parms._link = _parms._family.defaultLink;
      if (_parms._plug_values != null) {
        Frame plugValues = _parms._plug_values.get();
        if (plugValues == null) {
          error("_plug_values", "Supplied plug values frame with key=`" + _parms._plug_values + "` doesn't exist.");
        } else if (plugValues.numRows() != 1) {
          error("_plug_values", "Plug values frame needs to have exactly 1 row.");
        }
      }
      boolean standardizeQ = _parms._HGLM?false:_parms._standardize;
      _dinfo = new DataInfo(_train.clone(), _valid, 1, _parms._use_all_factor_levels || _parms._lambda_search, standardizeQ ? DataInfo.TransformType.STANDARDIZE : DataInfo.TransformType.NONE, DataInfo.TransformType.NONE, 
              _parms.missingValuesHandling() == MissingValuesHandling.Skip, 
              _parms.imputeMissing(),
              _parms.makeImputer(), 
              false, hasWeightCol(), hasOffsetCol(), hasFoldCol(), _parms.interactionSpec());
      _totalBetaLen = Family.multinomial.equals(_parms._family) || Family.ordinal.equals(_parms._family)?
              _dinfo.fullN()*nclasses()+1:_dinfo.fullN()+1;
      if (GLMType.gam.equals(_parms._glmType))
         _gamColIndices = extractAdaptedFrameIndices(_dinfo._adaptedFrame, _gamColnames, _dinfo._numOffsets[0]-_dinfo._cats);
        
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
      _state = new ComputationState(_job, _parms, _dinfo, null, nclasses(), _penaltyMatrix, _gamColIndices);
        
      // skipping extra rows? (outside of weights == 0)GLMT
      boolean skippingRows = (_parms.missingValuesHandling() == GLMParameters.MissingValuesHandling.Skip && _train.hasNAs());
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
        } else {
          _state._ymu = new double[]{_parms._intercept ? _train.lastVec().mean() : _parms.linkInv(0)};
        }
      }
      BetaConstraint bc = (_parms._beta_constraints != null)?new BetaConstraint(_parms._beta_constraints.get()):new BetaConstraint();
      if((bc.hasBounds() || bc.hasProximalPenalty()) && _parms._compute_p_values)
        error("_compute_p_values","P-values can not be computed for constrained problems");
      if(bc.hasBounds())
        _parms._early_stopping = false; // PUBDEV-4641: early stopping does not work correctly with non-negative option
      _state.setBC(bc);
      if(hasOffsetCol() && _parms._intercept) { // fit intercept
        GLMGradientSolver gslvr = new GLMGradientSolver(_job,_parms, _dinfo.filterExpandedColumns(new int[0]), 
                0, _state.activeBC(), _penaltyMatrix, _gamColIndices);
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
      double[] beta = getNullBeta();
      if (_parms._HGLM) {
        setHGLMInitValues(beta);
        _parms._lambda = new double[]{0}; // disable elastic-net regularization
      } else {
        if (_parms._startval != null) { // allow user start set initial beta values
          if (_parms._startval.length != beta.length) {
            throw new IllegalArgumentException("Initial coefficient length (" + _parms._startval.length + ") does not " +
                    "equal to actual GLM coefficient length(" + beta.length + ").\n  The order of coefficients should" +
                    " be the following:\n"+String.join("\n", _dinfo._adaptedFrame._names)+"\n Intercept.\n  " +
                    "Run your model without specifying startval to find out the actual coefficients names and " +
                    "lengths.");
          } else
            System.arraycopy(_parms._startval, 0, beta, 0, beta.length);
        }
        GLMGradientInfo ginfo = new GLMGradientSolver(_job, _parms, _dinfo, 0, _state.activeBC(), 
                _penaltyMatrix, _gamColIndices).getGradient(beta);  // gradient obtained with zero penalty
        _lmax = lmax(ginfo._gradient);
        _gmax = _lmax*Math.max(1e-2, _parms._alpha[0]); // each alpha should have its own best lambda
        _state.setLambdaMax(_lmax);
        _state.setgMax(_gmax);
        if (_parms._lambda_min_ratio == -1) {
          _parms._lambda_min_ratio = (_nobs >> 4) > _dinfo.fullN() ? 1e-4 : 1e-2;
          if (_parms._alpha[0] == 0)
            _parms._lambda_min_ratio *= 1e-2; // smalelr lambda min for ridge as we are starting quite high
        }
        _betaStart = new double[beta.length];
        System.arraycopy(beta, 0, _betaStart, 0, beta.length);
        _state.updateState(beta, ginfo);
        if (_parms._lambda == null) {  // no lambda given, we will base lambda as a fraction of lambda max
          if (_parms._lambda_search) {
            _parms._lambda = new double[_parms._nlambdas];
            double dec = Math.pow(_parms._lambda_min_ratio, 1.0 / (_parms._nlambdas - 1));
            _parms._lambda[0] = _lmax;
            double l = _lmax;
            for (int i = 1; i < _parms._nlambdas; ++i)
              _parms._lambda[i] = (l *= dec);
            // todo set the null submodel
          } else
            _parms._lambda = new double[]{10 * _parms._lambda_min_ratio * _lmax};
        }
        if (!Double.isNaN(_lambdaCVEstimate)) { // in main model, shrink the lambda range to search
          for (int i = 0; i < _parms._lambda.length; ++i)
            if (_parms._lambda[i] < _lambdaCVEstimate) {
              _parms._lambda = Arrays.copyOf(_parms._lambda, i + 1);
              break;
            }
          _parms._lambda[_parms._lambda.length - 1] = _lambdaCVEstimate;
          _parms._lambda[_parms._lambda.length - 1] = _lambdaCVEstimate;
        }
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

      if (_parms.hasCheckpoint()) {
        if (!Family.gaussian.equals(_parms._family))  // Gaussian it not iterative and therefore don't care
          _checkPointFirstIter = true;  // mark the first iteration during iteration process of training
        if (!Solver.IRLSM.equals(_parms._solver))
          error("_checkpoint", "GLM checkpoint is supported only for IRLSM.  Please specify it " +
                  "explicitly.  Do not use AUTO or default");
        Value cv = DKV.get(_parms._checkpoint);
        CheckpointUtils.getAndValidateCheckpointModel(this, CHECKPOINT_NON_MODIFIABLE_FIELDS, cv);
      }
      buildModel();
    }
  }

  /**
   * initialize the following parameters for HGLM from either user initial inputs or from a GLM model if user did not 
   * provide any starting values.
   * If user initial inputs are provided, it should be in the form of a big double array and the values should be
   * stacked according to the following sequence:
   * - beta, ubeta, phi, psi, tau, init_sig_e, init_sig_u
   * 
   * @param beta
   * 
   * Internal H2O method.  Do not use.
   */
  public void setHGLMInitValues(double[] beta) {
    _randC = new int[_parms._random_columns.length];  // _randC, array of random columns cardinalities
    _randomColNames = new String[_parms._random_columns.length];  // store random column names
    for (int rcInd = 0; rcInd < _parms._random_columns.length; rcInd++) {
      _randC[rcInd] = _parms.train().vec(_parms._random_columns[rcInd]).cardinality();
      _randomColNames[rcInd] = _parms.train().name(_parms._random_columns[rcInd]);
    }
    int fixedEffectSize = beta.length;
    int randomEffectSize = ArrayUtils.sum(_randC);
    _randCoeffNames = findExpandedRandColNames(); // column names for expanded random columns
    double tau=0; // store estimate of sig_e
    double[] phi = new double[randomEffectSize];
    double[] psi = new double[randomEffectSize];
    double[] ubeta = new double[randomEffectSize];
    double hlcorrection = 0;  // fixed for different distributions
    Vec tempVec = Vec.makeOne(randomEffectSize); // vector to store prior weights for random effects/columns
    // randWeights stores prior weight (0 col), calculated weight for random columns (1 col), zmi (intermediate values)
    Frame randWeights = new Frame(tempVec.makeOnes(3));
    randWeights.setNames(new String[]{"prior_weghts", "wpsi", "zmi"});  // add column names
    if (_parms._startval==null) {
      GLMModel tempModel = runGLMModel(_parms._standardize, Family.gaussian, Link.family_default, _parms._train,
              _parms._response_column, null, _parms._ignored_columns, false);
      System.arraycopy(tempModel.beta(), 0, beta, 0, beta.length);
      hex.ModelMetricsRegressionGLM tMetric =  (hex.ModelMetricsRegressionGLM) tempModel._output._training_metrics;
      double init_sig_e = 0.6*tMetric.residual_deviance()/tMetric.residual_degrees_of_freedom();
      double init_sig_u = init_sig_e*0.66;
      init_sig_e = restrictMag(init_sig_e); // make sure initial values are not too small
      init_sig_u = restrictMag(init_sig_u);
      Arrays.fill(phi, init_sig_u/_randC.length);
      tau = init_sig_e;
      _state.setHGLMComputationState(beta, ubeta, psi, phi, hlcorrection, tau, randWeights, _randCoeffNames);
      tempModel.remove();
      tMetric.remove();
    } else {
      copyUserInitialValues(fixedEffectSize, randomEffectSize, beta, ubeta, phi, hlcorrection, psi, randWeights,
              _randCoeffNames);
      _parms._startval = null;  // reset back to null
    }
  }

  /**
   * Construct random column coefficient names.
   * @return a string array containing random column coefficient names.
   */
  private String[] findExpandedRandColNames() {
    String[] randExpandedColNames = new String[ArrayUtils.sum(_randC)];
    int numRandCols = _randC.length;
    String[] randColNames = new String[numRandCols];
    int offset = 0;
    for (int index=0; index < numRandCols; index++) {
      int randomColIndex = _parms._random_columns[index];
      String[] domains = _parms.train().vec(randomColIndex).domain();
      int domainLen = domains.length;
      randColNames[index] = _parms.train().name(randomColIndex);
      for (int insideInd = 0; insideInd < domainLen; insideInd++) {
        randExpandedColNames[offset+insideInd] = randColNames[index]+"_"+insideInd;
      }
      offset += domainLen;
    }
    return randExpandedColNames;
  }

  public double restrictMag(double val) {
    if (val < 0.0001)
      return 0.1;
    else
      return val;
  }

  /**
   * This method performs a simple copying of user provided initial values to parameters
   * - beta, ubeta, phi, tau, psi, init_sig_u
   */
  public void copyUserInitialValues(int fixedEffectSize, int randomEffectSize, double[] beta,
                                        double[] ubeta, double[] phi, double hlcorrection, double[] psi,
                                        Frame randWeights, String[] randCoeffNames) {
    int off = 0;  // offset into startval
    int lengthLimit = fixedEffectSize;
    int totalstartvallen = fixedEffectSize+randomEffectSize+_randC.length+1;
    assert _parms._startval.length==totalstartvallen:"Expected startval length: "+totalstartvallen+", Actual" +
            " startval length: "+_parms._startval.length; // ensure startval contains enough initialization param
    for (int fixedInd=off; fixedInd < lengthLimit; fixedInd++) {
      beta[fixedInd] = _parms._startval[fixedInd];
    }
    off += fixedEffectSize;
    lengthLimit += randomEffectSize;
    for (int randomInd = off; randomInd < lengthLimit; randomInd++) {
      ubeta[randomInd-off] = _parms._startval[randomInd];
    }
    off += randomEffectSize;
    lengthLimit += _randC.length;
    int sig_u_off = 0;
    for (int siguInd=off; siguInd < lengthLimit; siguInd++) {
      double init_sig_u = _parms._startval[siguInd];
      for (int index=0; index < _randC[siguInd-off]; index++)
        phi[index+sig_u_off] = init_sig_u;
      sig_u_off += _randC[siguInd-off];
    }
    double tau = _parms._startval[lengthLimit];
    if (tau < 0.0001 || ArrayUtils.minValue(phi) < 0.0001)  // Following R thresholds
      error("init_sig_u, init_sig_e", "unacceptable initial values supplied for variance" +
              " parameter or dispersion parameter of the random effects.  They need to exceed 0.0001.");
    _state.setHGLMComputationState(beta, ubeta, psi, phi, hlcorrection, tau, randWeights, randCoeffNames);
  }

  /**
   * This method will quickly generate GLM model with proper parameter settings during the HGLM building process.
   * This is an internal H2O function.  Do not call.  It will change with no notice.
   * 
   * @param standardize
   * @param family
   * @param link
   * @param trainKey
   * @param responseColName
   * @param weightColumns
   * @param ignored_columns
   * @param computePValue
   * @return
   */
  private GLMModel runGLMModel(boolean standardize, Family family, Link link, Key<Frame> trainKey, 
                               String responseColName, String weightColumns, String[] ignored_columns, 
                               boolean computePValue) {
    GLMParameters tempParams = new GLMParameters();
    tempParams._train = trainKey;
    tempParams._family = family;
    tempParams._link = link;
    tempParams._lambda = new double[]{0};
    tempParams._standardize = standardize;
    tempParams._response_column = responseColName;
    tempParams._ignored_columns = ignored_columns;
    tempParams._weights_column = weightColumns;
    tempParams._compute_p_values = computePValue;
    tempParams._useDispersion1 = computePValue;
    GLMModel model = new GLM(tempParams).trainModel().get();
    return model;
  }
  
  // copy from scoring_history back to _sc or _lsc
  private void restoreScoringHistoryFromCheckpoint() {
      TwoDimTable scoringHistory = _model._output._scoring_history;
      String[] colHeaders2Restore = _parms._lambda_search ? 
              new String[]{"iteration", "timestamp", "lambda", "predictors", "deviance_train", 
                      "deviance_test", "alpha"}
              : new String[]{"iteration", "timestamp", "negative_log_likelihood", "objective", "sum(etai-eta0)^2", 
              "convergence"};
      int num2Copy = _parms._HGLM || _parms._lambda_search ? colHeaders2Restore.length : colHeaders2Restore.length-2;
      int[] colHeadersIndex = grabHeaderIndex(scoringHistory, num2Copy, colHeaders2Restore);
      if (_parms._lambda_search)
        _lambdaSearchScoringHistory.restoreFromCheckpoint(scoringHistory, colHeadersIndex);
      else
        _scoringHistory.restoreFromCheckpoint(scoringHistory, colHeadersIndex, _parms._HGLM);
  }
  
  static int[] grabHeaderIndex(TwoDimTable sHist, int numHeaders, String[] colHeadersUseful) {
    int[] colHeadersIndex = new int[numHeaders];
    List<String> colHeadersList = Arrays.asList(sHist.getColHeaders());
    for (int colInd = 0; colInd < numHeaders; colInd++) {
      if (colInd == 0) {
        int indexFound = colHeadersList.indexOf(colHeadersUseful[colInd]);
        if (indexFound < 0)
          indexFound = colHeadersList.indexOf(colHeadersUseful[colInd]+"s");
        colHeadersIndex[colInd] = indexFound;
      } else {
        colHeadersIndex[colInd] = colHeadersList.indexOf(colHeadersUseful[colInd]);
      }
    }
    return colHeadersIndex;
  }

  // FIXME: contrary to other models, GLM output duration includes computation of CV models:
  //  ideally the model should be instantiated in the #computeImpl() method instead of init
  private void buildModel() {
    if (_parms.hasCheckpoint()) {
      GLMModel model = ((GLMModel)DKV.getGet(_parms._checkpoint)).deepClone(_result);
      // Override original parameters by new parameters
      model._parms = _parms;
      // We create a new model
      _model = model;
      restoreScoringHistoryFromCheckpoint();  // copy over scoring history and related data structure
    } else {
      _model = new GLMModel(_result, _parms, this, _state._ymu, _dinfo._adaptedFrame.lastVec().sigma(), _lmax, _nobs);
    }
    _model._output.setLambdas(_parms);  // set lambda_min and lambda_max if lambda_search is enabled
    _model.delete_and_lock(_job);
  }

  protected static final long WORK_TOTAL = 1000000;

  transient Key [] _toRemove;

  private Key[] removeLater(Key ...k){
    _toRemove = _toRemove == null?k:ArrayUtils.append(_toRemove,k);
    return k;
  }

  @Override protected GLMDriver trainModelImpl() { return _driver = new GLMDriver(); }

  private final double lmax(double[] grad) {
    if (_parms._glmType==GLMType.gam) { // do not take into account gam col gradients.  They can be too big
      int totGamCols = 0;
      for (int numG = 0; numG < _penaltyMatrix.length; numG++) {
           totGamCols += _penaltyMatrix[numG].length;
      }
      int endIndex = grad.length - totGamCols;
      return Math.max(ArrayUtils.maxValue(grad,0,endIndex), -ArrayUtils.minValue(grad,0,endIndex)) 
              / Math.max(1e-2, _parms._alpha[0]);
    } else
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
        if (_parms._lambda_search && _parms._is_cv_model)
          Scope.untrack(removeLater(_dinfo.getWeightsVec()._key));
        if (_parms._HGLM) {
          Key[] vecKeys = _toRemove;
          for (int index = 0; index < vecKeys.length; index++) {
            Vec tempVec = DKV.getGet(vecKeys[index]);
            tempVec.remove();
          }
        }
      } catch (Exception e) {
        Log.err("Error while cleaning up GLM " + _result);
        Log.err(e);
      }
    }

    private transient Cholesky _chol;
    private transient L1Solver _lslvr;

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
    
    public Frame makeZeroOrOneFrame(long rowNumber, int colNumber, int val, String[] columnNames) {
      Vec tempVec = val==0?Vec.makeZero(rowNumber):Vec.makeOne(rowNumber);
      Frame madeFrame = val==0?new Frame(tempVec.makeZeros(colNumber)):new Frame(tempVec.makeOnes(colNumber));
      if (columnNames != null) {
        if (columnNames.length == colNumber)
          madeFrame.setNames(columnNames);
        else
          throw new IllegalArgumentException("Column names length and number of columns in Frame differ.");
      }
      cleanupHGLMMemory(null, null, new Vec[]{tempVec}, null);
      return madeFrame;
    }

    /**
     * This method will estimate beta, ubeta as described in step 2 of HGLM fitting algorithm.  Details can be found in 
     * the appendix.
     * @param randCatLevels
     * @param totRandCatLevels
     * @param returnFrame: will contain the calculation of dev, hv which will be used in step 3 and later.
     * @param dinfoWCol
     * @param wCol
     * @param etaOColIndexReturnFrame
     * @param dinfoResponseColID
     * @param sumEtaInfo
     * @param augXZ
     * @param cholR
     * @return
     */
    private double[] fitCoeffs(int[] randCatLevels, int totRandCatLevels, Frame returnFrame, int[] dinfoWCol, int[] wCol, 
                           int etaOColIndexReturnFrame, int dinfoResponseColID, double[] sumEtaInfo, Frame augXZ, 
                               double[][] cholR) {
      // qMatrix is used to store the Q matrix from QR decomposition of augXZ.  It is used in the loop here.
      Frame qMatrix=makeZeroOrOneFrame(_dinfo._adaptedFrame.numRows() + ArrayUtils.sum(_randC),
              _state.beta().length + _state.ubeta().length, 0, null);
      Frame augZW = makeZeroOrOneFrame(augXZ.numRows(), 1, 0, new String[]{"AugZ*W"});
      int betaLength = _state.beta().length;
      int ubetaLength = _state.ubeta().length;
      CalculateW4Data calculateW4Data;
      double[] start_delta = MemoryManager.malloc8d(betaLength + ubetaLength);
      int iteration=0;
      // store eta at the beginning and move it from _dinfo response columns to returnFrame for metrics calculation
      new CopyPartsOfFrame(_dinfo._adaptedFrame, new int[]{etaOColIndexReturnFrame}, 
              new int[]{_dinfo.responseChunkId(dinfoResponseColID)}, _dinfo._adaptedFrame.numRows()).doAll(returnFrame);
      new CalculateW4Rand(_job, _parms, randCatLevels, _state.get_psi(), _state.get_phi(),
              _state.ubeta()).doAll(_state.get_priorw_wpsi());
      new RandColAddW2AugXZ(_job, _randC, _state.get_priorw_wpsi(), 1, _dinfo._adaptedFrame.numRows(),
              augXZ.numCols()-ArrayUtils.sum(_randC), augXZ.numCols()).doAll(augXZ);
      do {  // start loop GLM.MME loop in R
        start_delta = calculate_all_beta(start_delta, augXZ, augZW, totRandCatLevels, cholR); // calculate new beta, ubeta
        new CopyPartsOfFrame(augXZ, null, null, qMatrix.numRows()).doAll(qMatrix); // copy Q matrix from augXZ to qMatrix
        _state.set_beta_HGLM(start_delta, 0, betaLength, true); // save new fixed/random coefficients to _state
        _state.set_ubeta_HGLM(start_delta, betaLength, ubetaLength);
        iteration++; // update iteration count
        // generate weight for data part and AugXZ with the new weights
        calculateW4Data = calculateNewWAugXZ(augXZ, _randC);
      } while (progressHGLMGLMMME(calculateW4Data._sumEtaDiffSq, calculateW4Data._sumEtaSq, iteration, true, 
              null, null, null, null, null, null, null, null));
      if (iteration > _parms._max_iterations) 
        Log.debug(LogMsg("HGLM GLM.MME did not converge in "+iteration+" iterations."));
      ReturnGLMMMERunInfoData glmRunInfoData = new ReturnGLMMMERunInfoData(_job, _dinfo, qMatrix, dinfoWCol, 
              _parms).doAll(returnFrame);
      ReturnGLMMMERunInfoRandCols glmRunInfo = new ReturnGLMMMERunInfoRandCols(_job, _dinfo, _state.get_priorw_wpsi(),
              qMatrix, wCol, _parms, _state.get_psi(), _state.ubeta(),
              ArrayUtils.cumsum(randCatLevels)).doAll(returnFrame);
      glmRunInfo._sumDev += glmRunInfoData._sumDev;
      new GenerateResid(_job, 
              1.0/Math.sqrt(glmRunInfo._sumDev/(returnFrame.numRows()-(betaLength+ubetaLength))),
              1, 4, _dinfo._adaptedFrame.numRows()).doAll(returnFrame); // generate resid
      sumEtaInfo[0] = glmRunInfoData._sumEtaDiffSq;
      sumEtaInfo[1] = glmRunInfoData._sumEtaSq;
      cleanupHGLMMemory(null, new Frame[]{augZW, qMatrix}, null, null);
      return sumEtaInfo;
    }
    
    public CalculateW4Data calculateNewWAugXZ(Frame augXZ, int[] randCatLevels) {
      // generate weight for data part
      CalculateW4Data calculateW4Data =  new CalculateW4Data(_job, _dinfo, _parms, randCatLevels, _state.beta(), _state.ubeta(),
              _state.get_psi(), _state.get_phi(), _state.get_tau(),
              _state.get_correction_HL()).doAll(_dinfo._adaptedFrame);
      // generate weight for lower part of AugXZ
      new CalculateW4Rand(_job, _parms, randCatLevels, _state.get_psi(), _state.get_phi(),
              _state.ubeta()).doAll(_state.get_priorw_wpsi());
      // generate AugXZ as [X | Z] * wdata for top half and bottom half as [0 | I]*wpsi
      new DataAddW2AugXZ(_job, _dinfo, _randC).doAll(augXZ);
      new RandColAddW2AugXZ(_job, _randC, _state.get_priorw_wpsi(), 1, _dinfo._adaptedFrame.numRows(), augXZ.numCols()-ArrayUtils.sum(_randC), augXZ.numCols()).doAll(augXZ);
      return calculateW4Data;
    }
    
    public void copyOver(double[][] cholR, double[][] cholRcopy) {
      int dim1 = cholR.length;
      int dim2 = cholR[0].length;
      for (int index=0; index < dim1; index++)
        System.arraycopy(cholR[index], 0, cholRcopy[index], 0, dim2);
    }

    /***
     * This method will estimate beta and ubeta using QR decomposition.  Refer to HGLM documentation appendix step 2.
     * @param start_delta
     * @param augXZ
     * @param augZW
     * @param totRandCatLevels
     * @param cholRcopy
     * @return
     */
    public double[] calculate_all_beta(double[] start_delta, Frame augXZ, Frame augZW, int totRandCatLevels, 
                                       double[][] cholRcopy) {
      // perform QR decomposition on augXZ and store R as a double[][] array, Q back in augXZ
      DataInfo augXZInfo = new DataInfo(augXZ, null, true, DataInfo.TransformType.NONE,
              true, false, false);
      DKV.put(augXZInfo._key, augXZInfo);
      // QR decomposition of augXZ, Q stored in augXZ, cholR stores transpose(R).
      double[][] cholR = ArrayUtils.transpose(LinearAlgebraUtils.computeQInPlace(_job._key, augXZInfo));
      copyOver(cholR, cholRcopy); // copy over R matrix, it is lower triangle, used in model metrics calculation later.
      Frame qTransposed = DMatrix.transpose(augXZ); // transpose Q (stored in Q) and store in qTransposed
      // generate frame augZW, it is one column only
      new CalculateAugZWData(_job, _dinfo, _dinfo.responseChunkId(1)).doAll(augZW);
      new CalculateAugZWRandCols(_job, _state.get_priorw_wpsi(), 1, 
              _dinfo._adaptedFrame.numRows()).doAll(augZW);
      double[][] augZWTransposed = new double[1][];
      augZWTransposed[0] = FrameUtils.asDoubles(augZW.vec(0)); // store augZW as array
      // generate transpose(Q)*AugZxW and put the result into an array
      DataInfo qTinfo = new DataInfo(qTransposed, null, true, DataInfo.TransformType.NONE,
              true, false, false);
      DKV.put(qTinfo._key, qTinfo);
      Frame qTAugZW = (new BMulTask(_job._key, qTinfo, augZWTransposed).doAll(augZWTransposed.length, Vec.T_NUM,
              qTinfo._adaptedFrame)).outputFrame(Key.make("Q*Augz*W"), null, null);
      double[] qtaugzw = new FrameUtils.Vec2ArryTsk((int) qTAugZW.numRows()).doAll(qTAugZW.vec(0)).res;
      // backward solve to get new coefficients for fixed and random columns
      start_delta = LinearAlgebraUtils.backwardSolve(cholR, qtaugzw, start_delta);
      cleanupHGLMMemory(new DataInfo[]{qTinfo, augXZInfo}, new Frame[]{qTransposed, qTAugZW}, null, 
              new Key[] {qTinfo._key, augXZInfo._key});
      return start_delta; // return newly estimated coefficients
    }

    /***
     * Method to clean up memories like Frames, DataInfos and Vectors after usage.
     * 
     * @param tempdInfo
     * @param tempFrames
     * @param tempVectors
     */
    private void cleanupHGLMMemory(DataInfo[] tempdInfo, Frame[] tempFrames, Vec[] tempVectors, Key[] dkvKeys) {
      if (tempdInfo != null) {
        for (int index=0; index < tempdInfo.length; index++)
          if (tempdInfo[index] != null)
            tempdInfo[index].remove();
      }
      if (tempFrames != null) {
        for (int index = 0; index < tempFrames.length; index++)
          if (tempFrames[index] != null)
            tempFrames[index].delete();
      }
      if (tempVectors != null) {
        for (int index = 0; index < tempVectors.length; index++)
          if (tempVectors[index] != null)
            tempVectors[index].remove();
      }
      if (dkvKeys != null) {
        for (int index=0; index < dkvKeys.length; index++) {
          if (dkvKeys[index]!= null)
            DKV.remove(dkvKeys[index]);
        }
      }
    }
    
    private void fitHGLM() {
      // figure out random columns categorical levels
      int numRandCols = _parms._random_columns.length;
      int totRandCatLevels = ArrayUtils.sum(_randC);
      int[] cumSumRandLevels = ArrayUtils.cumsum(_randC);
      // glmmmeReturnFrame is pre-allocated and stays alive for the whole HGLM model building process.  Hence, instead
      // of allocating and de-allocating it, I kept it around.  It has _nobs+number of expanded random columns in rows.
      // eta.i = Xi*beta, etao contains the old eta.i
      Frame glmmmeReturnFrame = makeZeroOrOneFrame(totRandCatLevels+_dinfo._adaptedFrame.numRows(),
              6,   0, new String[]{"AugZ", "hv", "dev", "eta.i", "resid", "etao"});
      // hvDataOnly stores the shortened hv column in glmmmeReturnFrame
      Frame hvDataOnly = makeZeroOrOneFrame(_dinfo._adaptedFrame.numRows(), 1, 0, new String[]{"hv_data"});
      // store column indices that contains HGLM info: response, zi, etaOld, prior_weight or response, zi, etaOld
      int[] dinfoWCol = _dinfo._weights?new int[]{_dinfo.responseChunkId(0), _dinfo.responseChunkId(2), 
              _dinfo.responseChunkId(3), _dinfo.weightChunkId()}:new int[]{_dinfo.responseChunkId(0), 
              _dinfo.responseChunkId(2), _dinfo.responseChunkId(3)};
      int[] wCol = new int[]{0, 2}; // in w_prior_wpsi, col 0 is prior weight, col 2 is zmi
      int[] devHvColIdx = new int[]{2,1}; // refer to column indices of dev, hv from glmmmeReturnFrame
      double[] sumEtaInfo = new double[2];  // first element is sum(eta.i-etao)^2, second element: sum(eta.i^2)
      long numDataRows = _dinfo._adaptedFrame.numRows();
      double[][] VC2 = new double[numRandCols][2];// store estimates and standard error of the random effects in the dispersion model
      double[] VC1 = new double[2]; // store estimates and standard error of the fixed predictor in the dispersion model
      int iter = 0;
      // augXZ stores Ta*W as described in HGLM documentation, it is used throughout the whole fitting process
      Frame augXZ = makeZeroOrOneFrame(_dinfo._adaptedFrame.numRows() + ArrayUtils.sum(_randC),
              _state.beta().length + _state.ubeta().length, 0, null);
      // generate weights for [X | Z] part of AugXZ and stored it in _dinfo
      // generate weight for data part
      CalculateW4Data calculateW4Data =  new CalculateW4Data(_job, _dinfo, _parms, _randC, _state.beta(), _state.ubeta(),
              _state.get_psi(), _state.get_phi(), _state.get_tau(),
              _state.get_correction_HL()).doAll(_dinfo._adaptedFrame);
      // generate AugXZ as [X | Z] * wdata for top half and bottom half as [0 | I]*wpsi
      new DataAddW2AugXZ(_job, _dinfo, _randC).doAll(augXZ);
      sumEtaInfo[0] = calculateW4Data._sumEtaDiffSq;
      sumEtaInfo[1] = calculateW4Data._sumEtaSq;
      _state.set_sumEtaSquareConvergence(sumEtaInfo);
      GLMModel fixedModel;
      GLMModel[] randModels = new GLMModel[numRandCols];
      updateProgress(null, null, null, null, null, null, 
              Double.NaN, Double.NaN, false, null, null);
      double[][] cholR = new double[augXZ.numCols()][augXZ.numCols()];  // store R from QR decomposition of augXZ

      do {
        // step 2, estimate beta, ubeta from HGLM documentation
        if (_state._iter > 0) {
          new CalculateW4Data(_job, _dinfo, _parms, _randC, _state.beta(), _state.ubeta(),
                  _state.get_psi(), _state.get_phi(), _state.get_tau(),
                  _state.get_correction_HL()).doAll(_dinfo._adaptedFrame);
          new DataAddW2AugXZ(_job, _dinfo, _randC).doAll(augXZ);
        }
        sumEtaInfo = fitCoeffs(_randC, totRandCatLevels, glmmmeReturnFrame, dinfoWCol, wCol, 
                5, 3, sumEtaInfo, augXZ, cholR);
        // step 3, estimate init_sig_e
        fixedModel = fitDataDispersion(glmmmeReturnFrame, devHvColIdx, VC1);
        // step 4, estimate init_sig_u
        estimateRandomCoeffCh(glmmmeReturnFrame, devHvColIdx, cumSumRandLevels, numRandCols, numDataRows, randModels, VC2);
      } while (progressHGLMGLMMME(sumEtaInfo[0], sumEtaInfo[1], iter, false, fixedModel, randModels, 
              glmmmeReturnFrame, hvDataOnly, VC1, VC2, cholR, augXZ));
      scoreAndUpdateModelHGLM(fixedModel, randModels, glmmmeReturnFrame, hvDataOnly, VC1, VC2, sumEtaInfo[0], 
              sumEtaInfo[1], cholR, augXZ, true);
      fixedModel.remove();
      cleanupHGLMMemory(null, new Frame[]{glmmmeReturnFrame, hvDataOnly, augXZ, _state.get_priorw_wpsi()}, null, null);
    }

    /***
     * This method will estimate the characteristics of the random coefficients for each random column.
     */
    private void estimateRandomCoeffCh(Frame returnFrame, int[] devHvColIdx, int[] cumSumRandLevels, int numRandCols, 
                                       long numDataRows, GLMModel[] gRandModels, double[][] VC2) {
      long dev2UseStart = numDataRows;
      int startIndex = 0;
      double[] phi = _state.get_phi();
      for (int colIndex=0; colIndex < numRandCols; colIndex++) {
        Frame constXYWeight = new Frame(makeZeroOrOneFrame(cumSumRandLevels[colIndex], 3, 1,
                new String[]{"response", "X", "weights"}));
        DKV.put(constXYWeight._key, constXYWeight);
        gRandModels[colIndex] = buildGammaGLM(returnFrame, constXYWeight, devHvColIdx, dev2UseStart, 
                (long) cumSumRandLevels[colIndex], true);  // generate frame to build glm model
         // update phi value with glmRand fitted value
        double sigma2u = Math.exp(gRandModels[colIndex].coefficients().get("Intercept"));
        double newPhi = sigma2u;
        for (int index=startIndex; index < cumSumRandLevels[colIndex]; index++)
          phi[index] = newPhi;
        _state.set_phi(phi);
        startIndex = cumSumRandLevels[colIndex];
        // set phi with new value in _state
        dev2UseStart += cumSumRandLevels[colIndex];
        assignEstStdErr(gRandModels[colIndex], VC2[colIndex]);
        cleanupHGLMMemory(null, new Frame[]{constXYWeight}, null, new Key[] {constXYWeight._key});
        gRandModels[colIndex].remove();
      }
    }

    /***
     * This method estimates the init_sig_e by building a gamma GLM with response 
     * @param returnFrame
     * @param devHvColIdx
     * @param VC1
     * @return
     */
    public GLMModel fitDataDispersion(Frame returnFrame, int[] devHvColIdx, double[] VC1) {
      // constXYWeight stores response, weights for Steps 4, 5 of the fitting algorithm.
      Frame constXYWeight = new Frame(makeZeroOrOneFrame(_dinfo._adaptedFrame.numRows(), 3, 1,
              new String[]{"response", "X", "weights"}));
      DKV.put(constXYWeight._key, constXYWeight);
      GLMModel gfixed = buildGammaGLM(returnFrame, constXYWeight, devHvColIdx, 0, 
              _dinfo._adaptedFrame.numRows(), true);  // build a gamma GLM
      double sigma2e = Math.exp(gfixed.coefficients().get("Intercept"));  // extra dispersion parameter
      _state.set_tau(sigma2e);
      assignEstStdErr(gfixed, VC1);
      cleanupHGLMMemory(null, new Frame[]{constXYWeight}, null, new Key[] {constXYWeight._key});
      gfixed.remove();
      return gfixed;  // return gamma GLM model
    }
    
    private void assignEstStdErr(GLMModel glm, double[] VC) {
      double[] stdErr = glm._output.stdErr();
      VC[0] = glm.coefficients().get("Intercept");
      VC[1] = stdErr[0];
    }

    /**
     * This method will generate a training frame according to HGLM doc, build a gamma GLM model with dispersion
     * factor set to 1 if enabled and calcluate the p-value if enabled.  It will return the GLM model.
     * 
     * @param returnFrame
     * @param constXYWeight
     * @param devHvColIdx
     * @param startRowIndex
     * @param numRows
     * @param computePValues
     * @return
     */
    public GLMModel buildGammaGLM(Frame returnFrame, Frame constXYWeight, int[] devHvColIdx, long startRowIndex, 
                                  long numRows, boolean computePValues) {
      // generate training frame constXYWeight from returnFrame
      new ExtractFrameFromSourceWithProcess(returnFrame, devHvColIdx, startRowIndex, numRows).doAll(constXYWeight);
      DKV.put(constXYWeight._key, constXYWeight);
      boolean originalPValues = _parms._compute_p_values; // enable p value computation if needed
      boolean originalDispersion = _parms._useDispersion1;
      _parms._compute_p_values = computePValues;
      _parms._useDispersion1 = computePValues;  // set dispersion to 1 if needed
      GLMModel g11 = runGLMModel(_parms._standardize, Family.gamma, Link.log, constXYWeight._key, "response",
              "weights",null, computePValues);  // generate gamma GLM model
      _parms._compute_p_values = originalPValues;
      _parms._useDispersion1 = originalDispersion;
      return g11;
    }

    private void fitIRLSM_multinomial(Solver s) {
      assert _dinfo._responses == 3 : "IRLSM for multinomial needs extra information encoded in additional reponses, expected 3 response vecs, got " + _dinfo._responses;

      if (Solver.COORDINATE_DESCENT.equals(s)) {
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
      double [] beta = _parms._solver == Solver.COORDINATE_DESCENT?COD_solve(gramXY,_state._alpha,_state.lambda())
              :ADMM_solve(gramXY.gram,gramXY.xy);
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
      double [] betaCnd = _checkPointFirstIter ? _model._betaCndCheckpoint : _state.beta();
      LineSearchSolver ls = null;
      int iterCnt = _checkPointFirstIter?_state._iter:0;
      boolean firstIter = iterCnt == 0;
      try {
        while (true) {
          iterCnt++;
          long t1 = System.currentTimeMillis();
          ComputationState.GramXY gram = _state.computeGram(betaCnd,s);
          long t2 = System.currentTimeMillis();
          if (!_state._lsNeeded && (Double.isNaN(gram.likelihood) || _state.objective(gram.beta, gram.likelihood) > 
                  _state.objective() + _parms._objective_epsilon) && !_checkPointFirstIter) {
            _state._lsNeeded = true;
          } else {
            if (!firstIter && !_state._lsNeeded && !progress(gram.beta, gram.likelihood) && !_checkPointFirstIter) {
              Log.info("DONE after " + (iterCnt-1) + " iterations (1)");
              _model._betaCndCheckpoint = betaCnd;
              return;
            }
            if (!_checkPointFirstIter)
              betaCnd = s == Solver.COORDINATE_DESCENT ? COD_solve(gram, _state._alpha, _state.lambda())
                      : ADMM_solve(gram.gram, gram.xy); // this will shrink betaCnd if needed but this call may be skipped
          }
          firstIter = false;
          _checkPointFirstIter = false;
          long t3 = System.currentTimeMillis();
          if(_state._lsNeeded) {
            if(ls == null)
              ls = (_state.l1pen() == 0 && !_state.activeBC().hasBounds())
                 ? new MoreThuente(_state.gslvr(),_state.beta(), _state.ginfo())
                 : new SimpleBacktrackingLS(_state.gslvr(),_state.beta().clone(), _state.l1pen(), _state.ginfo());
            double[] oldBetaCnd = ls.getX();
            if (betaCnd.length != oldBetaCnd.length) {  // if ln 1453 is skipped and betaCnd.length != _state.beta()
              betaCnd = _state.extractSubRange(betaCnd.length, 0, _state.activeData()._activeCols, betaCnd);
            }
            if (!ls.evaluate(ArrayUtils.subtract(betaCnd, oldBetaCnd, betaCnd))) { // ls.getX() get the old beta value
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
          rho[i] = r * ADMM.L1Solver.estimateRho(nullBeta[i] + t * direction[i], l1pen, 
                  bc._betaLB == null ? Double.NEGATIVE_INFINITY : bc._betaLB[i], 
                  bc._betaUB == null ? Double.POSITIVE_INFINITY : bc._betaUB[i]);
        for (int ii = P; ii < rho.length; ii += P + 1)
          rho[ii] = r * ADMM.L1Solver.estimateRho(nullBeta[ii] + t * direction[ii], 0, 
                  bc._betaLB == null ? Double.NEGATIVE_INFINITY : bc._betaLB[ii], bc._betaUB == null ? 
                          Double.POSITIVE_INFINITY : bc._betaUB[ii]);
        final double[] objvals = new double[2];
        objvals[1] = Double.POSITIVE_INFINITY;
        double reltol = L1Solver.DEFAULT_RELTOL;
        double abstol = L1Solver.DEFAULT_ABSTOL;
        double ADMM_gradEps = 1e-3;
        ProximalGradientSolver innerSolver = new ProximalGradientSolver(gslvr, beta, rho, 
                _parms._objective_epsilon * 1e-1, _parms._gradient_epsilon, _state.ginfo(), this);
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
        if(!_parms._lambda_search && _state._iter == 0 && !_parms._HGLM)
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
        Log.debug("iter1 = " + iter1);
      }
      Log.debug("iter2 = " + iter2);
      long endTimeTotalNaive = System.currentTimeMillis();
      long durationTotalNaive = (endTimeTotalNaive - startTimeTotalNaive)/1000;
      Log.info("Time to run Naive Coordinate Descent " + durationTotalNaive);
      _state._iter = iter2;
      for (Vec v : newVecs) v.remove();
      _state.updateState(beta,objold);
    }
    private void fitModel() {
      Solver solver = (_parms._solver == Solver.AUTO) ? defaultSolver() : _parms._solver;
      if (_parms._HGLM) {
        fitHGLM();
      } else {
        switch (solver) {
          case COORDINATE_DESCENT: // fall through to IRLSM
          case IRLSM:
            if (_parms._family == Family.multinomial)
              fitIRLSM_multinomial(solver);
            else if (_parms._family == Family.ordinal)
              fitIRLSM_ordinal_default(solver);
            else if (_parms._family == Family.gaussian && _parms._link == Link.identity)
              fitLSM(solver);
            else
              fitIRLSM(solver);
            break;
          case GRADIENT_DESCENT_LH:
          case GRADIENT_DESCENT_SQERR:
            if (Family.ordinal.equals(_parms._family))
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
      }
      if(_parms._compute_p_values) { // compute p-values
        double se = 1;
        boolean seEst = false;
        double [] beta = _state.beta();

        if(_parms._family != Family.binomial && _parms._family != Family.poisson) {
          seEst = true;
          ComputeSETsk ct = new ComputeSETsk(null, _state.activeData(), _job._key, beta, _parms).doAll(_state.activeData()._adaptedFrame);
          if (_parms._useDispersion1)
            se = 1.0;
          else
            se = ct._sumsqe / (_nobs - 1 - _state.activeData().fullN());  // this is the dispersion parameter estimate
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

    /***
     * performm score and update for HGLM models.  However, here, there is no scoring, only metrics update.
     * @param fixedModel
     * @param randModels
     * @param glmmmeReturns
     */
    private void scoreAndUpdateModelHGLM(GLMModel fixedModel, GLMModel[] randModels, Frame glmmmeReturns, 
                                         Frame hvDataOnly, double[] VC1, double[][] VC2, double sumDiff2, 
                                         double convergence, double[][] cholR, Frame augXZ, boolean compute_hlik) {
      Log.info(LogMsg("Scoring after " + timeSinceLastScoring() + "ms"));
      long t1 = System.currentTimeMillis();
      Frame train = DKV.<Frame>getGet(_parms._train); // need to keep this frame to get scoring metrics back
      String[] domain = new String[]{"HGLM_"+_parms._family.toString()+"_"+_parms._rand_family.toString()};
      ModelMetrics.MetricBuilder mb = _model.makeMetricBuilder(domain);
      ModelMetricsHGLM.MetricBuilderHGLM mmHGLMBd = (ModelMetricsHGLM.MetricBuilderHGLM) (((GLMMetricBuilder) mb)._metricBuilder);
      updateSimpleHGLMMetrics(fixedModel, randModels, VC1, VC2, mmHGLMBd, sumDiff2, convergence);
      calBad(glmmmeReturns, hvDataOnly, mmHGLMBd);
      calseFeseRedfReFe(cholR, mmHGLMBd, augXZ);
      if (_parms._calc_like && compute_hlik) { // computation/memory intensive, only calculated it if needed
        calHlikStuff(mmHGLMBd, glmmmeReturns, augXZ);
        _state.set_likelihoodInfo(mmHGLMBd._hlik, mmHGLMBd._pvh, mmHGLMBd._pbvh, mmHGLMBd._caic);
      }
      mb.makeModelMetrics(_model, train, _dinfo._adaptedFrame, null);  // add generated metric to DKV
      scorePostProcessing(train, t1);
    }
    
    private void calHlikStuff(ModelMetricsHGLM.MetricBuilderHGLM mmHGLMBd, Frame glmmmeReturns, Frame augXZ) {
      calculateNewWAugXZ(augXZ, _randC);
      double cond_hlik = calculatecAIC(mmHGLMBd, glmmmeReturns);
      Frame hlikH = formHMatrix(augXZ);
      mmHGLMBd._hlik = cond_hlik;
      mmHGLMBd._pvh = cond_hlik-0.5*calcLogDeterminant(hlikH);  // logdet(H/(2pi)) is correct
      Frame hlikA = formAMatrix(hlikH, augXZ);
      mmHGLMBd._pbvh = cond_hlik-0.5*calcLogDeterminant(hlikA);
      cleanupHGLMMemory(null, new Frame[]{hlikH, hlikA}, null, null);
    }
    
    private double calcLogDeterminant(Frame frame) {  // calculate the log determinant for frame/(2*Math.PI)
      SVDModel.SVDParameters parms = new SVDModel.SVDParameters();
      parms._train = frame._key;
      parms._transform = DataInfo.TransformType.NONE;
      parms._svd_method = SVDParameters.Method.GramSVD;
      parms._save_v_frame = false;
      parms._nv = frame.numCols();
      SVDModel model = new SVD(parms).trainModel().get();
      double[] singular_values = model._output._d;
      double cond = ArrayUtils.minValue(singular_values)/ArrayUtils.maxValue(singular_values);
      if (cond < 0.00000001)  // value copied from R
        warn("pbvh", "The Hessian used for computing pbvh is ill-conditioned.");
      double sumLog = 0.0;
      double log2Pi = Math.log(2*Math.PI);
      for (int index=0; index < parms._nv; index++)
        sumLog += Math.log(singular_values[index])-log2Pi;
      model.delete(); // clean up model before proceeding.
      return sumLog;
    }

    private Frame formAMatrix(Frame hlikH, Frame augXZ) {
      Frame dataFrame = getXW1(augXZ);
      DataInfo hlikAInfo = new DataInfo(dataFrame, null, true, DataInfo.TransformType.NONE,
              true, false, false);
      Gram.GramTask dgram = new Gram.GramTask(_job._key, hlikAInfo, false, false).doAll(hlikAInfo._adaptedFrame);
      Frame leftUp = new ArrayUtils().frame(dgram._gram.getXX());
      Frame augzW1 = getaugZW1(augXZ);
      Frame tX = DMatrix.transpose(dataFrame);
      int expandedRandColNum = ArrayUtils.sum(_randC);
      tX.add(new Frame(makeZeroOrOneFrame(tX.numRows(), expandedRandColNum, 0, null)));
      DKV.put(augzW1._key, augzW1);
      new LinearAlgebraUtils.BMulTaskMatrices(augzW1).doAll(tX);
      Frame tXW1z = tX.subframe(tX.numCols()-expandedRandColNum, tX.numCols());
      leftUp.add(tXW1z);
      Frame leftDown = DMatrix.transpose(tXW1z);
      leftDown._key = Key.make();
      leftDown.add(hlikH);
      leftDown.setNames(leftUp.names());
      DKV.put(leftDown._key, leftDown);
      DKV.put(leftUp._key, leftUp);
      String tree = String.format("(rbind %s %s)", leftUp._key, leftDown._key);
      Val val = Rapids.exec(tree);
      Frame amatrix = val.getFrame();
      amatrix._key = Key.make();
      DKV.put(amatrix._key, amatrix);
      cleanupHGLMMemory(new DataInfo[]{hlikAInfo}, new Frame[]{dataFrame, leftUp, augzW1, tX, tXW1z, leftDown}, null, null);
      return amatrix;
    }

    private Frame getXW1(Frame augXZ) {
      int numDataCols = augXZ.numCols()-ArrayUtils.sum(_randC);
      int[] colIndices = new int[numDataCols];
      for (int index=0; index<numDataCols; index++)
        colIndices[index]=index;
      Frame dataFrame = new Frame(makeZeroOrOneFrame(_nobs, numDataCols, 0, null));
      new CopyPartsOfFrame(augXZ, colIndices, colIndices, _nobs).doAll(dataFrame);
      return dataFrame;
    }


    private Frame formHMatrix(Frame augXZ) {
      Frame augZW1 = getaugZW1(augXZ);
      DataInfo augZW1Info = new DataInfo(augZW1, null, true, DataInfo.TransformType.NONE,
              true, false, false);
      Gram.GramTask dgram = new Gram.GramTask(_job._key, augZW1Info, false, false).doAll(augZW1Info._adaptedFrame);
      Frame wranddata = _state.get_priorw_wpsi();
      double[][] W2 = null;
      W2 = ArrayUtils.transpose(new ArrayUtils.FrameToArray(1,1, wranddata.numRows(), W2).doAll(wranddata).getArray());
      ArrayUtils.mult(W2[0], W2[0]);        // generate W2 square
      dgram._gram.addDiag(W2[0]);
      cleanupHGLMMemory(new DataInfo[]{augZW1Info}, new Frame[]{augZW1, wranddata}, null, null);
      return new ArrayUtils().frame(dgram._gram.getXX());
    }
    
    private Frame getaugZW1(Frame augXZ) {
      int numRandExpandedCols = ArrayUtils.sum(_randC);
      int randIndexStart = augXZ.numCols()-numRandExpandedCols;
      Frame augZW1 = new Frame(makeZeroOrOneFrame(_nobs, numRandExpandedCols, 0, null));
      int[] colIndices = new int[numRandExpandedCols];
      int colNum = augXZ.numCols();
      for (int index=randIndexStart; index<colNum; index++) {
        colIndices[index-randIndexStart] = index;
      }
      new CopyPartsOfFrame(augXZ, null, colIndices,_nobs).doAll(augZW1);
      return augZW1;
    }
    
    private double calculatecAIC(ModelMetricsHGLM.MetricBuilderHGLM mmHGLMBd, Frame glmmmeReturns) {
      Frame hv_dev_pw = new Frame(makeZeroOrOneFrame(_nobs, 2, 0, new String[]{"hv", "dev"}));
      new CopyPartsOfFrame(glmmmeReturns, new int[]{0,1}, new int[]{1,2},_nobs).doAll(hv_dev_pw);
      if (_dinfo._weights) 
        hv_dev_pw.add("weights", _dinfo._adaptedFrame.vec(_dinfo.weightChunkId())); // frame could have 2 or 3 columns
      HelpercAIC calcAIC = new HelpercAIC(_dinfo._weights, mmHGLMBd._varfix).doAll(hv_dev_pw);
      double constance = -0.5*calcAIC._constT;
      double cond_hlik = constance-0.5*calcAIC._devOphi;
      mmHGLMBd._caic = -2*cond_hlik+2*calcAIC._p;
      // add contribution from lfv and sum(log(abs(du_dv))) which is zero for Gaussian
      double[] lfvals = lfv_du_dv(_parms._rand_family, _parms._rand_link, _state.get_phi(), _state.ubeta());
      cond_hlik += lfvals[0]+lfvals[1];
      hv_dev_pw.remove();
      return cond_hlik;
    }

    public double[] lfv_du_dv(Family[] family, Link[] link, double[] phi, double[] u) {
      double[] vals = new double[2];  // first element stores lfv, second for du_dv
      int numRandCols = _randC.length;
      for (int k=0; k < numRandCols; k++) { // go over each random column
        Family tfamily = family[k];
        Link tlink = tfamily.defaultLink;
        if (!(link==null))
          tlink=link[k];
        GLMWeightsFun glmfun = new GLMWeightsFun(tfamily, tlink,
                0, 0, 0);
        int colLength = _randC[k];
        for (int col=0; col < colLength; col++) {
          int index = k*colLength+col;
          if (Family.gaussian.equals(tfamily)) {  // only implementation now
            if (Link.identity.equals(tlink)) {
              vals[1] += Math.log(Math.abs(glmfun.linkInvDeriv(glmfun.link(u[index]))));
              vals[0] -= Math.log(Math.sqrt(2*Math.PI))+Math.log(Math.sqrt(phi[index]))+u[index]*u[index]/(2*phi[index]);
            }
          }
        }
      }
      return vals;
    }

    /***
     * This method will calculate the HGLM metrics seFe, seRe, dfReFe
     * @param cholR
     */
    private void calseFeseRedfReFe(double[][] cholR, ModelMetricsHGLM.MetricBuilderHGLM mmHGLMBd, Frame augXZ) {
      double[][] RTRInv = LinearAlgebraUtils.chol2Inv(cholR);  // should be the transpose but care about the diagonal.
      double[] seFeRe = LinearAlgebraUtils.sqrtDiag(RTRInv);
      int sefelen = _state.beta().length;
      int serelen = _state.ubeta().length;
      if (mmHGLMBd._sefe ==null)
        mmHGLMBd._sefe = new double[sefelen];
      System.arraycopy(seFeRe, 0, mmHGLMBd._sefe, 0, sefelen);
      if (mmHGLMBd._sere == null)
        mmHGLMBd._sere = new double[serelen];
      System.arraycopy(seFeRe, sefelen, mmHGLMBd._sere, 0, serelen);
      Frame augZ = new Frame(makeZeroOrOneFrame(_nobs, augXZ.numCols(), 0,
              null));
      new CopyPartsOfFrame(augXZ, null, null,_nobs).doAll(augZ);
      DataInfo augzInfo = new DataInfo(augZ, null, true, DataInfo.TransformType.NONE, 
              true, false, false);
      Gram.GramTask dgram = new Gram.GramTask(_job._key, augzInfo, false, false).doAll(augzInfo._adaptedFrame);
      double[][] gramMatrix = dgram._gram.getXX();
      double pd = ArrayUtils.sum(ArrayUtils.diagArray(LinearAlgebraUtils.matrixMultiply(RTRInv, gramMatrix)));
      mmHGLMBd._dfrefe = Math.round(_nobs-pd);
      cleanupHGLMMemory(new DataInfo[] {augzInfo}, new Frame[]{augZ}, null, null);
    }
  
    private void calBad(Frame glmmeReturns, Frame hvFrameOnly, ModelMetricsHGLM.MetricBuilderHGLM mmHGLMBd) {
      new CopyPartsOfFrame(glmmeReturns, new int[]{0}, new int[]{1},_nobs).doAll(hvFrameOnly);
      Vec vec = hvFrameOnly.vec(0);
      double sigma6 = vec.mean()+6*vec.sigma();
      double maxVec = vec.max();
      if (maxVec > sigma6) {
         mmHGLMBd._bad = (new FindMaxIndex(0, maxVec).doAll(hvFrameOnly))._maxIndex;
      } else
        mmHGLMBd._bad=-1;
    }
    
    private void updateSimpleHGLMMetrics(GLMModel fixedModel, GLMModel[] randModels, double[] VC1, double[][] VC2, 
                                         ModelMetricsHGLM.MetricBuilderHGLM mmHGLMBd, double sumDiff2, double convergence) {
      mmHGLMBd.updateCoeffs(_state.beta(), _state.ubeta()); // update coefficients
      mmHGLMBd.updateSummVC(VC1, VC2, _randC);                      // update summVC1 and summVC2
      mmHGLMBd._varfix = Math.exp(fixedModel.coefficients().get("Intercept"));  // update sigmas for coefficients
      int randColNum = mmHGLMBd._randc.length;
      if (mmHGLMBd._varranef ==null)
        mmHGLMBd._varranef = new double[randColNum];
      boolean converged = true;
      double sumSigma2u = 0;
      for (int index = 0; index < randColNum; index++) {
        mmHGLMBd._varranef[index] = Math.exp(randModels[index].coefficients().get("Intercept"));
        sumSigma2u += mmHGLMBd._varranef[index];
      }
      for (int index = 0; index < randColNum; index++)  {
        if ((mmHGLMBd._varranef[index]/(sumSigma2u+mmHGLMBd._varfix)) > 0.9999) { // 0.9999 from R
          converged = false;
          break;
        }
      }
      mmHGLMBd._converge = converged && (_state._iter < _parms._max_iterations);
      mmHGLMBd._sumetadiffsquare = sumDiff2;
      mmHGLMBd._convergence = convergence;
      mmHGLMBd._iterations = _state._iter;
      mmHGLMBd._nobs = _nobs;
    }
    
    private void scoreAndUpdateModel(){
      // compute full validation on train and test
      Log.info(LogMsg("Scoring after " + timeSinceLastScoring() + "ms"));
      long t1 = System.currentTimeMillis();
      Frame train = DKV.<Frame>getGet(_parms._train); // need to keep this frame to get scoring metrics back
      _model.score(train).delete();
      scorePostProcessing(train, t1);
    }
    
    private void scorePostProcessing(Frame train, long t1) {
//      _scoreIterationList.add(_state._iter);
      ModelMetrics mtrain = ModelMetrics.getFromDKV(_model, train); // updated by model.scoreAndUpdateModel
      long t2 = System.currentTimeMillis();
      if (_parms._lambda_search)
        _model._output._scoring_history = _lambdaSearchScoringHistory.to2dTable();
      else if (_parms._HGLM)
        _model._output._scoring_history = _scoringHistory.to2dTableHGLM();
      else
        _model._output._scoring_history = _scoringHistory.to2dTable();
      if (!(mtrain == null)) {
        _model._output._training_metrics = mtrain;
        _model._output._training_time_ms = t2-_model._output._start_time; // remember training time
        ScoreKeeper trainScore = new ScoreKeeper(Double.NaN);
        trainScore.fillFrom(mtrain);
        Log.info(LogMsg(mtrain.toString()));
      } else {
        Log.info(LogMsg("ModelMetrics mtrain is null"));
      }
      Log.info(LogMsg("Training metrics computed in " + (t2 - t1) + "ms"));
      if (_valid != null) {
        Frame valid = DKV.<Frame>getGet(_parms._valid);
        _model.score(valid).delete();
        _model._output._validation_metrics = ModelMetrics.getFromDKV(_model, valid); //updated by model.scoreAndUpdateModel
        ScoreKeeper validScore = new ScoreKeeper(Double.NaN);
        validScore.fillFrom(_model._output._validation_metrics);
      }
      
      _model.addScoringInfo(_parms, nclasses(), t2);  // add to scoringInfo for early stopping
      _model.update(_job._key);
      if (_parms._HGLM)
        _model.generateSummaryHGLM(_parms._train,_state._iter);
      else
        _model.generateSummary(_parms._train,_state._iter);
      _lastScore = System.currentTimeMillis();
      long scoringTime = System.currentTimeMillis() - t1;
      _scoringInterval = Math.max(_scoringInterval,20*scoringTime); // at most 5% overhead for scoring
    }
    
    private void coldStart(double[] devHistoryTrain, double[] devHistoryTest) {
      _state.setBeta(_betaStart);  // reset beta to original starting condition
      _state.setIter(0);
      _state.setLambdaSimple(0.0);  // reset to 0 before new lambda is assigned
      _state._currGram = null;
      _state.setBetaDiff(_betaDiffStart);
      _state.setGradientErr(0.0);
      _state.setGinfo(_ginfoStart);
      _state.setLikelihood(_ginfoStart._likelihood);
      _state.setAllIn(false);
      _state.setGslvrNull();
      _state.setActiveDataMultinomialNull();
      _state.setActiveDataNull();
      int histLen = devHistoryTrain.length;
      for (int ind=0; ind < histLen; ind++) {
        devHistoryTrain[ind]=0;
        devHistoryTest[ind]=0;
      }
    }
    
    protected Submodel computeSubmodel(int i,double lambda, double nullDevTrain, double nullDevValid) {
      Submodel sm;
      boolean continueFromPreviousSubmodel = _parms.hasCheckpoint() && (_parms._alpha.length > 1 || 
              _parms._lambda.length > 1) && _checkPointFirstIter && !Family.gaussian.equals(_parms._family);
      if(lambda >= _lmax && _state.l1pen() > 0) {
        if (continueFromPreviousSubmodel)
          sm = _model._output._submodels[i];
        else
          _model.addSubmodel(sm = new Submodel(lambda, _state.alpha(), getNullBeta(), _state._iter, nullDevTrain, nullDevValid));
      } else {  // this is also the path for HGLM model
        if (continueFromPreviousSubmodel) {
          sm = _model._output._submodels[i];
        } else {
          sm = new Submodel(lambda, _state.alpha(), _state.beta(), _state._iter, -1, -1);// restart from last run
          if (_parms._HGLM) // add random coefficients for random effects/columns
            sm.ubeta = Arrays.copyOf(_state.ubeta(), _state.ubeta().length);
          _model.addSubmodel(sm);
        }
        if (!_parms._HGLM) {  // only perform this when HGLM is not used.
          if (!_checkPointFirstIter)
            _state.setLambda(lambda);
        }
        
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
        if (_parms._HGLM) {
          sm = new Submodel(lambda, _state.alpha(), _state.beta(), _state._iter, nullDevTrain, nullDevValid);
          sm.ubeta = Arrays.copyOf(_state.ubeta(),_state.ubeta().length);
          _model.updateSubmodel(sm);
        } else {
          double trainDev = _state.deviance() / _nobs;
          double validDev = Double.NaN;  // calculated from validation dataset below if present
          if (_validDinfo != null) {  // calculate deviance for validation set and save as testDev
            if (_parms._family == Family.ordinal)
              validDev = new GLMResDevTaskOrdinal(_job._key, _validDinfo, _dinfo.denormalizeBeta(_state.beta()), _nclass).doAll(_validDinfo._adaptedFrame).avgDev();
            else
              validDev = _parms._family == Family.multinomial
                      ? new GLMResDevTaskMultinomial(_job._key, _validDinfo, _dinfo.denormalizeBeta(_state.beta()), _nclass).doAll(_validDinfo._adaptedFrame).avgDev()
                      : new GLMResDevTask(_job._key, _validDinfo, _parms, _dinfo.denormalizeBeta(_state.beta())).doAll(_validDinfo._adaptedFrame).avgDev();
          }
          Log.info(LogMsg("train deviance = " + trainDev + ", valid deviance = " + validDev));
          double xvalDev = ((_xval_deviances == null) || (_xval_deviances.length <= i)) ? -1 : _xval_deviances[i];
          double xvalDevSE = ((_xval_sd == null) || (_xval_deviances.length <= i)) ? -1 : _xval_sd[i];
          _lambdaSearchScoringHistory.addLambdaScore(_state._iter, ArrayUtils.countNonzeros(_state.beta()), 
                  _state.lambda(), trainDev, validDev, xvalDev, xvalDevSE, _state.alpha()); // add to scoring history
          _model.updateSubmodel(sm = new Submodel(_state.lambda(), _state.alpha(), _state.beta(), _state._iter, 
                  trainDev, validDev));
        }
      }
      return sm;
    }
    
    @Override
    public void computeImpl() {
      try {
        doCompute();
      } finally {
        if (_model != null) _model.unlock(_job);
      }
    }
    
    private void doCompute() {
      double nullDevTrain = Double.NaN;
      double nullDevValid = Double.NaN;
      if(_doInit)
        init(true);
      if (error_count() > 0)
        throw H2OModelBuilderIllegalArgumentException.makeFromBuilder(GLM.this);
      _model._output._start_time = System.currentTimeMillis(); //quickfix to align output duration with other models
      if(_parms._lambda_search) {
        if (_parms._family == Family.ordinal)
          nullDevTrain = new GLMResDevTaskOrdinal(_job._key, _state._dinfo, getNullBeta(), _nclass).doAll(_state._dinfo._adaptedFrame).avgDev();
        else
        nullDevTrain =  _parms._family == Family.multinomial
          ?new GLMResDevTaskMultinomial(_job._key,_state._dinfo,getNullBeta(), _nclass).doAll(_state._dinfo._adaptedFrame).avgDev()
          :new GLMResDevTask(_job._key, _state._dinfo, _parms, getNullBeta()).doAll(_state._dinfo._adaptedFrame).avgDev();
        if(_validDinfo != null) {
          if (_parms._family == Family.ordinal)
            nullDevValid = new GLMResDevTaskOrdinal(_job._key, _validDinfo, getNullBeta(), _nclass).doAll(_validDinfo._adaptedFrame).avgDev();
          else
          nullDevValid = _parms._family == Family.multinomial
                  ? new GLMResDevTaskMultinomial(_job._key, _validDinfo, getNullBeta(), _nclass).doAll(_validDinfo._adaptedFrame).avgDev()
                  : new GLMResDevTask(_job._key, _validDinfo, _parms, getNullBeta()).doAll(_validDinfo._adaptedFrame).avgDev();
        }
        _workPerIteration = WORK_TOTAL/_parms._nlambdas;
      } else
        _workPerIteration = 1 + (WORK_TOTAL/_parms._max_iterations);

      if((_parms._family == Family.multinomial || _parms._family == Family.ordinal) && _parms._solver != Solver.L_BFGS ) {
        double[] nb = getNullBeta();
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
      
      if (_parms._HGLM) { // add w, augZ, etaOld and random columns to response for easy access inside _dinfo
        addWdataZiEtaOld2Response();
      }
      
      double oldDevTrain = nullDevTrain;
      double oldDevTest = nullDevValid;
      double [] devHistoryTrain = new double[5];
      double [] devHistoryTest = new double[5];

      if (!_parms._HGLM) {  // only need these for non HGLM
        _ginfoStart = GLMUtils.copyGInfo(_state.ginfo());
        _betaDiffStart = _state.getBetaDiff();
      }

      if (_parms.hasCheckpoint()) { // restore _state parameters
        _state.copyCheckModel2State(_model, _gamColIndices);
        if (_model._output._submodels.length == 1)
          _model._output._submodels = null; // null out submodel only for single alpha/lambda values
      }

      if(!_parms._lambda_search & !_parms._HGLM)
        updateProgress(false);
      
      // alpha, lambda search loop
      int alphaStart = 0;
      int lambdaStart = 0;
      int submodelCount = 0;
      if (_parms.hasCheckpoint() && _model._output._submodels != null) {  // multiple alpha/lambdas or lambda search
        submodelCount = Family.gaussian.equals(_parms._family)?_model._output._submodels.length
                :_model._output._submodels.length-1;
        alphaStart = submodelCount / _parms._lambda.length;
        lambdaStart = submodelCount % _parms._lambda.length;
      }
      _model._output._lambda_array_size = _parms._lambda.length;
      for (int alphaInd = alphaStart; alphaInd < _parms._alpha.length; alphaInd++) {
        _state.setAlpha(_parms._alpha[alphaInd]);   // loop through the alphas
        if ((!_parms._HGLM) && (alphaInd > 0) && !_checkPointFirstIter) // no need for cold start during the first iteration
          coldStart(devHistoryTrain, devHistoryTest);  // reset beta, lambda, currGram
        for (int i = lambdaStart; i < _parms._lambda.length; ++i) {  // for lambda search, can quit before it is done
          if (_job.stop_requested() || (timeout() && _model._output._submodels.length > 0))
            break;  //need at least one submodel on timeout to avoid issues.
          if (_parms._max_iterations != -1 && _state._iter >= _parms._max_iterations) 
            break;  // iterations accumulate across all lambda/alpha values when coldstart = false
          if ((!_parms._HGLM && (_parms._cold_start || (!_parms._lambda_search && _parms._cold_start))) && (i > 0) 
                  && !_checkPointFirstIter) // default: cold_start for non lambda_search
            coldStart(devHistoryTrain, devHistoryTest);
          Submodel sm = computeSubmodel(submodelCount, _parms._lambda[i], nullDevTrain, nullDevValid);
          if (_checkPointFirstIter)
            _checkPointFirstIter = false;
          double trainDev = sm.devianceTrain; // this is stupid, they are always -1 except for lambda_search=True
          double testDev = sm.devianceValid;
          devHistoryTest[submodelCount % devHistoryTest.length] = 
                  (oldDevTest - testDev) / oldDevTest; // only remembers 5
          oldDevTest = testDev;
          devHistoryTrain[submodelCount % devHistoryTrain.length] = 
                  (oldDevTrain - trainDev) / oldDevTrain;
          oldDevTrain = trainDev;
          if (_parms._lambda[i] < _lmax && Double.isNaN(_lambdaCVEstimate) /** if we have cv lambda estimate we should use it, can not stop before reaching it */) {
            if (_parms._early_stopping && _state._iter >= devHistoryTrain.length) {
              double s = ArrayUtils.maxValue(devHistoryTrain);
              if (s < 1e-4) {
                Log.info(LogMsg("converged at lambda[" + i + "] = " + _parms._lambda[i]+ "alpha[" + alphaInd + "] = "
                        + _parms._alpha[alphaInd]+ ", improvement on train = " + s));
                break; // started overfitting
              }
              if (_validDinfo != null && _parms._nfolds <= 1) { // check for early stopping on test with no xval
                s = ArrayUtils.maxValue(devHistoryTest);
                if (s < 0) {
                  Log.info(LogMsg("converged at lambda[" + i + "] = " + _parms._lambda[i] +  "alpha[" + alphaInd +
                          "] = " + _parms._alpha[alphaInd]+", improvement on test = " + s));
                  break; // started overfitting
                }
              }
            }
          }

          if (_parms._lambda_search && (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval)) {
            _model._output.setSubmodelIdx(_model._output._best_submodel_idx = submodelCount); // quick and easy way to set submodel parameters
            scoreAndUpdateModel(); // update partial results
          }
          _job.update(_workPerIteration, "iter=" + _state._iter + " lmb=" + 
                  lambdaFormatter.format(_state.lambda()) + " alpha=" + lambdaFormatter.format(_state.alpha())+ 
                  "deviance trn/tst= " + devFormatter.format(trainDev) + "/" + devFormatter.format(testDev) + 
                  " P=" + ArrayUtils.countNonzeros(_state.beta()));
          submodelCount++;  // updata submodel index count here
        }
      }

      if (stop_requested() || _earlyStop) {
        if (timeout()) {
          Log.info("Stopping GLM training because of timeout");
        } else if (_earlyStop) {
          Log.info("Stopping GLM training due to hitting earlyStopping criteria.");
        } else {
          throw new Job.JobCancelledException();
        }
      }
      if(_state._iter >= _parms._max_iterations)
        _job.warn("Reached maximum number of iterations " + _parms._max_iterations + "!");
      if(_parms._nfolds > 1 && !Double.isNaN(_lambdaCVEstimate))
        _model._output.setSubmodelIdx(_model._output._best_submodel_idx=_bestCVSubmodel);  // reset best_submodel_idx to what xval has found
      else
        _model._output.pickBestModel();
      if(_vcov != null) { // should move this up, otherwise, scoring will never use info in _vcov
        _model.setVcov(_vcov);
        _model.update(_job._key);
      }
      if (!_parms._HGLM)  // no need to do for HGLM
        scoreAndUpdateModel();
      TwoDimTable scoring_history_early_stop = ScoringInfo.createScoringHistoryTable(_model.getScoringInfo(),
              (null != _parms._valid), false, _model._output.getModelCategory(), false);
      _model._output._scoring_history = combineScoringHistory(_model._output._scoring_history,
              scoring_history_early_stop, (_parms._lambda_search ? _lambdaSearchScoringHistory._lambdaIters : _scoringHistory._scoringIters));
      _model._output._varimp = _model._output.calculateVarimp();
      _model._output._variable_importances = calcVarImp(_model._output._varimp);
      _model.update(_job._key);
/*      if (_vcov != null) {
        _model.setVcov(_vcov);
        _model.update(_job._key);
      }*/
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
    }

    /***
     * Internal H2O method.  Do not use.
     * This method will add three more columns to _dinfo.response for easy access later
     * - column 1: wdata, calculated weight for fixed columns/effects
     * - column 2: zi, intermediate values
     * - column 3: eta = X*beta, intermediate values
     */
    public void addWdataZiEtaOld2Response() { // attach wdata, zi, eta to response for HGLM
      int moreColnum = 3 + _parms._random_columns.length;
      Vec[] vecs = _dinfo._adaptedFrame.anyVec().makeZeros(moreColnum);
      String[] colNames = new String[moreColnum];
      colNames[0] = "wData";  // store weight w for data rows only
      colNames[1] = "zi";
      colNames[2] = "etaOld";
      int[] randColIndices = _parms._random_columns;
      for (int index=3; index < moreColnum; index++) {
        colNames[index] = _parms.train().name(index-3);
        vecs[index] = _parms.train().vec(randColIndices[index-3]).makeCopy();
      }
      _dinfo.addResponse(colNames, vecs);
      for (int index=0; index < moreColnum; index++ ) {
        Scope.untrack(vecs[index]._key);
        removeLater(vecs[index]._key);
      }
    }

    @Override
    public void onCompletion(CountedCompleter caller) {
      doCleanup();
      super.onCompletion(caller);
    }

    @Override public boolean onExceptionalCompletion(Throwable t, CountedCompleter caller){
      doCleanup();
      return super.onExceptionalCompletion(t, caller);
    }


    @Override public boolean progress(double [] beta, GradientInfo ginfo) {
      _state._iter++;
      if(ginfo instanceof ProximalGradientInfo) {
        ginfo = ((ProximalGradientInfo) ginfo)._origGinfo;
        GLMGradientInfo gginfo = (GLMGradientInfo) ginfo;
        _state.updateState(beta, gginfo);
        if (!_parms._lambda_search)
          updateProgress(false);
        return !stop_requested() && _state._iter < _parms._max_iterations && !_earlyStop;
      } else {
        GLMGradientInfo gginfo = (GLMGradientInfo) ginfo;
        if(gginfo._gradient == null)
          _state.updateState(beta,gginfo._likelihood);
        else
          _state.updateState(beta, gginfo);
        if (!_parms._lambda_search)
          updateProgress(true);
        boolean converged = !_earlyStopEnabled && _state.converged(); // GLM specific early stop.  Disabled if early stop is enabled
        if (converged) Log.info(LogMsg(_state.convergenceMsg));
        return !stop_requested() && !converged && _state._iter < _parms._max_iterations && !_earlyStop;
      }
    }

    public boolean progressHGLMGLMMME(double sumDiff2, double sumeta2, int iteration, boolean atGLMMME, GLMModel 
            fixedModel, GLMModel[] randModels, Frame glmmmeReturns, Frame hvDataOnly, double[] VC1, double[][] VC2,
                                      double[][] cholR, Frame augZ) {
      boolean converged = !_earlyStopEnabled && (sumDiff2 < (_parms._objective_epsilon*sumeta2));
      if (atGLMMME) {
        _state._iterHGLM_GLMMME++;
      } else {
          _state._iter++;
          updateProgress(fixedModel, randModels, glmmmeReturns, hvDataOnly, VC1, VC2, sumDiff2,
                  sumDiff2 / sumeta2, true, cholR, augZ);
      }
      return !stop_requested() && !converged && (iteration < _parms._max_iterations) && !_earlyStop;
    }
    
    public boolean progress(double [] beta, double likelihood) {
      _state._iter++;
      _state.updateState(beta,likelihood);
      if(!_parms._lambda_search)
        updateProgress(true);
      boolean converged = !_earlyStopEnabled && _state.converged();
      if(converged) Log.info(LogMsg(_state.convergenceMsg));
      return !stop_requested() && !converged && _state._iter < _parms._max_iterations && !_earlyStop;
    }

    private transient long _scoringInterval = SCORING_INTERVAL_MSEC;

    protected void updateProgress(GLMModel fixedModel, GLMModel[] randModels, Frame glmmmeReturns, Frame hvDataOnly, 
                                  double[] VC1, double[][] VC2, double sumDiff2, double convergence, boolean canScore,
                                  double[][] cholR, Frame augXZ) {
      _scoringHistory.addIterationScore(_state._iter, _state._sumEtaSquareConvergence);
      if(canScore && (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval || 
              ((_parms._score_iteration_interval > 0) && ((_state._iter % _parms._score_iteration_interval) == 0)))) {
        _model.update(_state.expandBeta(_state.beta()), _state.ubeta(),-1, -1, _state._iter);
        scoreAndUpdateModelHGLM(fixedModel, randModels, glmmmeReturns, hvDataOnly, VC1, VC2, sumDiff2, convergence, 
                cholR, augXZ, false);
        _earlyStop = updateEarlyStop();
      }
    }
    // update user visible progress
    protected void updateProgress(boolean canScore){
      assert !_parms._lambda_search;
      _scoringHistory.addIterationScore(_state._iter, _state.likelihood(), _state.objective());
      _job.update(_workPerIteration,_state.toString());
      if(canScore && (_parms._score_each_iteration || timeSinceLastScoring() > _scoringInterval)) {
        _model.update(_state.expandBeta(_state.beta()), -1, -1, _state._iter);
        scoreAndUpdateModel();
        _earlyStop = updateEarlyStop();
      }
    }
  }

  private boolean updateEarlyStop() {
    return _earlyStop || ScoreKeeper.stopEarly(_model.scoreKeepers(),
            _parms._stopping_rounds, ScoreKeeper.ProblemType.forSupervised(_nclass > 1), _parms._stopping_metric,
            _parms._stopping_tolerance, "model's last", true);
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

    public ProximalGradientSolver(GradientSolver s, double[] betaStart, double[] rho, double objEps, double gradEps,
                                  GradientInfo ginfo,ProgressMonitor pm) {
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
    double[][][] _penaltyMatrix;
    int[][] _gamColIndices;

    public GLMGradientSolver(Job job, GLMParameters glmp, DataInfo dinfo, double l2pen, BetaConstraint bc) {
      _job = job;
      _bc = bc;
      _parms = glmp;
      _dinfo = dinfo;
      _l2pen = l2pen;
    }

    public GLMGradientSolver(Job job, GLMParameters glmp, DataInfo dinfo, double l2pen, BetaConstraint bc, 
                             double[][][] penaltyMat, int[][] gamColInd) {
      this(job, glmp, dinfo, l2pen, bc);
      _penaltyMatrix = penaltyMat;
      _gamColIndices=gamColInd;
    }

    /*
    Only update the likelihood function for multinomial while leaving all else stale and old.  This is only
    used by multinomial with COD.
     */
    public GLMGradientInfo getMultinomialLikelihood(double[] beta) {
      assert Family.multinomial.equals(_parms._family) : "GLMGradientInfo.getMultinomialLikelihood is only used by multinomial GLM";
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
      double smoothval = GLMType.gam.equals(_parms._glmType)?calSmoothNess(_betaMultinomial, _penaltyMatrix, 
              _gamColIndices):0;
      return new GLMGradientInfo(gt._likelihood, gt._likelihood * _parms._obj_reg + .5 * _l2pen * l2pen + 
              smoothval, null);
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
                _parms, _penaltyMatrix, _gamColIndices).doAll(_dinfo._adaptedFrame);
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
        double smoothVal = GLMType.gam.equals(_parms._glmType)?calSmoothNess(_betaMultinomial, _penaltyMatrix,
                _gamColIndices):0.0;
        return new GLMGradientInfo(gt._likelihood, gt._likelihood * _parms._obj_reg + .5 * _l2pen * l2pen + 
                smoothVal, grad);
      } else {
        assert beta.length == _dinfo.fullN() + 1;
        assert _parms._intercept || (beta[beta.length-1] == 0);
        GLMGradientTask gt;
        if((_parms._family == Family.binomial && _parms._link == Link.logit) ||
                (_parms._family == Family.fractionalbinomial && _parms._link == Link.logit))
          gt = new GLMBinomialGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta, _penaltyMatrix, 
                  _gamColIndices).doAll(_dinfo._adaptedFrame);
        else if(_parms._family == Family.gaussian && _parms._link == Link.identity)
          gt = new GLMGaussianGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta, _penaltyMatrix,
                  _gamColIndices).doAll(_dinfo._adaptedFrame);
        else if (Family.negativebinomial.equals(_parms._family))
          gt =  new GLMNegativeBinomialGradientTask(_job == null?null:_job._key,_dinfo,
                  _parms,_l2pen, beta, _penaltyMatrix, _gamColIndices).doAll(_dinfo._adaptedFrame);
        else if(_parms._family == Family.poisson && _parms._link == Link.log)
          gt = new GLMPoissonGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta, _penaltyMatrix, 
                  _gamColIndices).doAll(_dinfo._adaptedFrame);
        else if(_parms._family == Family.quasibinomial)
          gt = new GLMQuasiBinomialGradientTask(_job == null?null:_job._key,_dinfo,_parms,_l2pen, beta, 
                  _penaltyMatrix, _gamColIndices).doAll(_dinfo._adaptedFrame);
        else
          gt = new GLMGenericGradientTask(_job == null?null:_job._key, _dinfo, _parms, _l2pen, beta, _penaltyMatrix, 
                  _gamColIndices).doAll(_dinfo._adaptedFrame);
        double [] gradient = gt._gradient;
        double  likelihood = gt._likelihood;
        if (!_parms._intercept) // no intercept, null the ginfo
          gradient[gradient.length - 1] = 0;
        
        double gamSmooth = GLMType.gam.equals(_parms._glmType)?
                calSmoothNess(expandVec(beta, _dinfo._activeCols, _totalBetaLen), _penaltyMatrix, _gamColIndices):0;
        double obj = likelihood * _parms._obj_reg + .5 * _l2pen * ArrayUtils.l2norm2(beta, true)+gamSmooth;
        if (_bc != null && _bc._betaGiven != null && _bc._rho != null)
          obj = ProximalGradientSolver.proximal_gradient(gradient, obj, beta, _bc._betaGiven, _bc._rho);
        return new GLMGradientInfo(likelihood, obj, gradient);
      }
    }

    @Override
    public GradientInfo getObjective(double[] beta) {
      double l = new GLMResDevTask(_job._key,_dinfo,_parms,beta).doAll(_dinfo._adaptedFrame)._likelihood;
      double smoothness = GLMType.gam.equals(_parms._glmType)?
              calSmoothNess(expandVec(beta, _dinfo._activeCols, _totalBetaLen), _penaltyMatrix, _gamColIndices):0;
      return new GLMGradientInfo(l,l*_parms._obj_reg + .5*_l2pen*ArrayUtils.l2norm2(beta,true)
              +smoothness,null);
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

    private Frame encodeCategoricalsIfPresent(Frame beta_constraints) {
      FrameUtils.BetaConstraintsEncoder constraintsEncoder = new FrameUtils.BetaConstraintsEncoder(_dinfo.coefNames(), _dinfo.coefOriginalNames());
      Frame transformedFrame =  constraintsEncoder.doAll(getBetaConstraintsTypesForEncoder(beta_constraints), beta_constraints).outputFrame();
      transformedFrame.setNames(beta_constraints._names);
      return transformedFrame;
    }
    
    private byte[] getBetaConstraintsTypesForEncoder(Frame beta_constraints) {
      byte[] types = beta_constraints.types();
      int id = Arrays.asList(beta_constraints.names()).indexOf("names");
      types[id] = T_STR;
      return types;
      
    }

    public BetaConstraint(Frame beta_constraints) {
      beta_constraints = encodeCategoricalsIfPresent(beta_constraints);
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
            if (idx >= _dinfo.numStart() && idx < _dinfo.fullN()) {
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
            if (idx >= _dinfo.numStart() && idx < _dinfo.fullN()) {
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

  public static class PlugValuesImputer implements DataInfo.Imputer { // make public to allow access to other algos
    private final Frame _plug_vals;

    public PlugValuesImputer(Frame plugValues) {
      _plug_vals = plugValues;
    }

    @Override
    public int imputeCat(String name, Vec v, boolean useAllFactorLevels) {
      String[] domain = v.domain();
      Vec pvec = pvec(name);
      String value;
      if (pvec.isCategorical()) {
        value = pvec.domain()[(int) pvec.at(0)];
      } else if (pvec.isString()) {
        value = pvec.stringAt(0);
      } else {
        throw new IllegalStateException("Plug value for a categorical column `" + name + "` cannot by of type " + pvec.get_type_str() + "!");
      }
      int valueIndex = ArrayUtils.find(domain, value);
      if (valueIndex < 0) {
        throw new IllegalStateException("Plug value `" + value + "` of column `" + name + "` is not a member of the column's domain!");
      }
      return valueIndex;
    }

    @Override
    public double imputeNum(String name, Vec v) {
      Vec pvec = pvec(name);
      if (v.isNumeric() || v.isTime()) {
        return pvec.at(0);
      } else {
        throw new IllegalStateException("Plug value for a column `" + name + "` of type " + v.get_type_str() + " cannot by of type " + pvec.get_type_str() + "!");
      }
    }

    @Override
    public double[] imputeInteraction(String name, InteractionWrappedVec iv, double[] means) {
      if (iv.isNumericInteraction()) {
        return new double[]{imputeNum(name, iv)};
      }
      assert iv.v1Domain() == null || iv.v2Domain() == null; // case when both vecs are categorical is handled by imputeCat
      String[] domain = iv.v1Domain() != null ? iv.v1Domain() : iv.v2Domain();
      double[] vals = new double[domain.length];
      for (int i = 0; i < domain.length; i++) {
        vals[i] = pvec(name + "." + domain[i]).at(0);
      }
      return vals;
    }

    private Vec pvec(String name) {
      Vec pvec = _plug_vals.vec(name);
      if (pvec == null) {
        throw new IllegalStateException("Plug value for column `" + name + "` is not defined!");
      }
      return pvec;
    }
  }

}
