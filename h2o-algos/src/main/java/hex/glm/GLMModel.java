package hex.glm;

import hex.*;
import hex.DataInfo.TransformType;
import hex.api.MakeGLMModelHandler;
import hex.deeplearning.DeepLearningModel;
import hex.genmodel.utils.DistributionFamily;
import hex.glm.GLMModel.GLMParameters.Family;
import hex.glm.GLMModel.GLMParameters.Link;
import hex.util.EffectiveParametersUtils;
import org.apache.commons.math3.distribution.NormalDistribution;
import org.apache.commons.math3.distribution.RealDistribution;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.special.Gamma;
import water.*;
import water.codegen.CodeGenerator;
import water.codegen.CodeGeneratorPipeline;
import water.exceptions.JCodeSB;
import water.fvec.Chunk;
import water.fvec.Frame;
import water.fvec.NewChunk;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.*;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static hex.DistributionFactory.LogExpUtil.log;
import static hex.genmodel.utils.ArrayUtils.flat;
import static hex.glm.ComputationState.expandToFullArray;
import static hex.glm.GLMModel.GLMOutput.calculatePValuesFromZValues;
import static hex.glm.GLMModel.GLMOutput.calculateStdErrFromZValues;
import static hex.glm.GLMUtils.genGLMParameters;
import static hex.modelselection.ModelSelectionUtils.extractPredictorNames;
import static hex.schemas.GLMModelV3.GLMModelOutputV3.calculateVarimpMultinomial;
import static hex.schemas.GLMModelV3.calculateVarimpBase;
import static hex.util.DistributionUtils.distributionToFamily;
import static hex.util.DistributionUtils.familyToDistribution;
import static java.util.stream.Collectors.toMap;

/**
 * Created by tomasnykodym on 8/27/14.
 */
public class GLMModel extends Model<GLMModel,GLMModel.GLMParameters,GLMModel.GLMOutput> implements Model.Contributions{

  final static public double _EPS = 1e-6;
  final static public double _OneOEPS = 1e6;

  public GLMModel(Key selfKey, GLMParameters parms, GLM job, double [] ymu, double ySigma, double lambda_max, long nobs) {
    super(selfKey, parms, job == null?new GLMOutput():new GLMOutput(job));
    _ymu = ymu;
    _ySigma = ySigma;
    _lambda_max = lambda_max;
    _nobs = nobs;
    _nullDOF = nobs - (parms._intercept?1:0);
  }
  
  public Frame getRIDFrame() {
    if (_output._regression_influence_diagnostics != null)
      return DKV.getGet(_output._regression_influence_diagnostics);
    else 
      return null;
  }

  @Override
  public void initActualParamValues() {
    super.initActualParamValues();
    EffectiveParametersUtils.initFoldAssignment(_parms);
  }
  
  public ScoreKeeper[] scoreKeepers() {
    int size = scoringInfo==null?0:scoringInfo.length;
    ScoreKeeper[] sk = new ScoreKeeper[size];
    for (int i=0;i<size;++i) {
      if (scoringInfo[i].cross_validation) // preference is to use xval first, then valid and last train.
        sk[i] = scoringInfo[i].scored_xval;
      else if (scoringInfo[i].validation)
        sk[i] = scoringInfo[i].scored_valid;
      else
        sk[i] = scoringInfo[i].scored_train;
    }
    return sk;
  }
  
  public ScoringInfo[] getScoringInfo() { return scoringInfo;}
  
  public void addScoringInfo(GLMParameters parms, int nclasses, long currTime, int iter) {
    if (scoringInfo != null && (((GLMScoringInfo) scoringInfo[scoringInfo.length-1]).iterations() >= iter)) {  // no duplication
      return;
    }
    GLMScoringInfo currInfo = new GLMScoringInfo();
    currInfo.is_classification = nclasses > 1;
    currInfo.validation = parms.valid() != null;
    currInfo.cross_validation = parms._nfolds > 1;
    currInfo.iterations = iter;
    currInfo.time_stamp_ms = scoringInfo==null?_output._start_time:currTime;
    currInfo.total_training_time_ms = _output._training_time_ms;
    if (_output._training_metrics != null) {
      currInfo.scored_train = new ScoreKeeper(Double.NaN);
      currInfo.scored_train.fillFrom(_output._training_metrics);
    }
    if (_output._validation_metrics != null) {
      currInfo.scored_valid = new ScoreKeeper(Double.NaN);
      currInfo.scored_valid.fillFrom(_output._validation_metrics);
    }
    scoringInfo = ScoringInfo.prependScoringInfo(currInfo, scoringInfo);
  }
  
  public void setVcov(double[][] inv) {_output._vcov = inv;}

  /***
   * This method will calculate the variable inflation factor of each numerical predictor using the following
   * procedure:
   * 1. For each numerical predictor, choose it as the response variable and leave all the other predictors as the
   * predictors;
   * 2. Build a GLM model and obtained the R2 of the model;
   * 3. the variable inflation factor for that predictor is calculated as 1.0/(1.0-R2).
   * 
   * All the variable inflation factors for all valid predictors are saved in a double array.  No variable inflation
   * factors are generated for non-numerical predictors.
   * 
   */
  public String[] buildVariableInflationFactors(Frame train, DataInfo dinfo) {
    String[] predictorNames = extractPredictorNames(_parms, dinfo, _parms._fold_column);
    // only calculate VIF for numerical columns
    String[] vifPredictors = getVifPredictors(train, _parms, dinfo);
    return buildVariableInflationFactors(_parms, vifPredictors, predictorNames);
  }

  static String[] getVifPredictors(Frame train, GLMParameters parms, DataInfo dinfo) {
    String[] predictorNames = extractPredictorNames(parms, dinfo, parms._fold_column);
    return Stream.of(predictorNames)
            .filter(x -> train.find(x) >= 0 && train.vec(x).isNumeric())
            .toArray(String[]::new);
  }

  public String[] buildVariableInflationFactors(GLMParameters parms, String[] validPredictors, String[] predictorNames) {
    // set variable inflation factors to NaN to start with
    _output._variable_inflation_factors = IntStream.range(0, validPredictors.length).mapToDouble(x -> Double.NaN).boxed().
            collect(Collectors.toList()).stream().mapToDouble(Double::doubleValue).toArray();
    GLMParameters[] allParams = genGLMParameters(parms, validPredictors, predictorNames);
    GLM[] glmBuilder = Stream.of(allParams).map(x -> new GLM(x)).collect(Collectors.toList()).stream().toArray(GLM[]::new);
    int parallelization = nVIFModelsInParallel(parms);
    GLM[] glmResults = ModelBuilderHelper.trainModelsParallel(glmBuilder, parallelization);
    Double[] r2 = Arrays.stream(glmResults).mapToDouble(x ->x.get().r2()).boxed().collect(Collectors.toList()).stream()
            .toArray(Double[]::new);
    for (GLM glm : glmResults)
      glm.get().remove();
    _output._variable_inflation_factors = IntStream.range(0, validPredictors.length)
            .mapToDouble(x -> 1.0 / (1.0 - r2[x])).boxed().collect(Collectors.toList()).stream()
            .mapToDouble(Double::doubleValue).toArray();
    return validPredictors;
  }

  static int nVIFModelsInParallel(GLMParameters parms) {
    if (parms._is_cv_model) { // CV is already parallelized
      return 1;
    }
    String userSpec = H2O.getSysProperty("glm.vif." + parms._train + ".nparallelism", null);
    if (userSpec != null) {
      try {
        final int vifParallelization = Integer.parseInt(userSpec);
        if (vifParallelization <= 0 || vifParallelization > H2O.ARGS.nthreads) {
          Log.warn("Ignoring user-specified parallelization level for VIF calculation. " +
                  "Value '" + userSpec + "' is out of range (0, nthreads].");
        }
        return vifParallelization;
      } catch (Exception e) {
        Log.err("Invalid user-specified parallelization level. Cannot parse value '" + userSpec + "' as a number.", e);
      }
    }
    Frame train = parms.train();
    if (train != null && train.byteSize() < 1e6) {
      return H2O.ARGS.nthreads; // VIF is relatively lightweight, on small data run all concurrently
    }
    return 2; // same strategy as in CV of "big data" - run 2 models concurrently
  }

  class GLMContributionsWithBackground extends ContributionsWithBackgroundFrameTask<GLMContributionsWithBackground> {
    double[] _betas;
    DataInfo _dinfo;
    boolean _compact;
    boolean _outputSpace;

    GLMContributionsWithBackground(DataInfo dinfo, double[] betas, Key<Frame> frameKey, Key<Frame> backgroundFrameKey, boolean perReference, boolean compact, boolean outputSpace) {
      super(frameKey, backgroundFrameKey, perReference);
      _dinfo = dinfo;
      _betas = betas;
      _compact = compact;
      _outputSpace = outputSpace;
    }

    @Override
    public void map(Chunk[] cs, Chunk[] bgCs, NewChunk[] ncs) {
      DataInfo.Row row = _dinfo.newDenseRow();
      DataInfo.Row bgRow = _dinfo.newDenseRow();
      int idx;
      double[] result = MemoryManager.malloc8d(ncs.length - 1); // used only when output_format == compact
      double transformationRatio = 1; // used for transforming to output space 
      for (int j = 0; j < cs[0]._len; j++) {
        _dinfo.extractDenseRow(cs, j, row);

        for (int k = 0; k < bgCs[0]._len; k++) {
          _dinfo.extractDenseRow(bgCs, k, bgRow);
          Arrays.fill(result, 0);
          double biasTerm = (_dinfo._intercept ? _betas[_betas.length - 1] : 0);
          for (int i = 0; i < _betas.length - (_dinfo._intercept ? 1 : 0); i++) {
            idx = _compact ? _dinfo.coefOriginalColumnIndices()[i] : i;
            result[idx] += _betas[i] * (row.get(i) - bgRow.get(i));
            biasTerm += _betas[i] * bgRow.get(i);
          }

          if (_outputSpace) {
            final double linkSpaceX = Arrays.stream(result).sum()+biasTerm;
            final double linkSpaceBg = biasTerm;
            final double outSpaceX = _parms.linkInv(linkSpaceX);
            final double outSpaceBg = _parms.linkInv(linkSpaceBg);
            transformationRatio = Math.abs(linkSpaceX - linkSpaceBg) < 1e-6 ? 0 : (outSpaceX - outSpaceBg) / (linkSpaceX - linkSpaceBg);
            biasTerm = outSpaceBg;
          }

          for (int i = 0; i < result.length; i++) {
            ncs[i].addNum(result[i] * transformationRatio);
          }
          ncs[ncs.length - 1].addNum(biasTerm);
        }
      }
    }
  }


  @Override
  public Frame scoreContributions(Frame frame, Key<Frame> destination_key, Job<Frame> j, ContributionsOptions options, Frame backgroundFrame) {
    assert GLMParameters.GLMType.glm.equals(_parms._glmType);

    if (ContributionsOutputFormat.Compact.equals(options._outputFormat)) {
      if (_parms._interactions != null && _parms._interactions.length > 0) {
        throw H2O.unimpl("SHAP for GLM with interactions is not supported");
      }
    }

    if (null == backgroundFrame)
      throw H2O.unimpl("GLM supports contribution calculation only with a background frame.");
    Log.info("Starting contributions calculation for " + this._key + "...");
    try (Scope.Safe s = Scope.safe(frame, backgroundFrame)) {
      Frame adaptedBgFrame = adaptFrameForScore(backgroundFrame, false);
      Frame adaptedFrame = adaptFrameForScore(frame, false);
      DKV.put(adaptedBgFrame);
      DKV.put(adaptedFrame);
      DataInfo dinfo = _output._dinfo.clone();
      dinfo._adaptedFrame = adaptedFrame;
      GLMContributionsWithBackground contributions = new GLMContributionsWithBackground(dinfo,
              _parms._standardize ? _output.getNormBeta() : _output.beta(), adaptedFrame._key, adaptedBgFrame._key,
              options._outputPerReference, ContributionsOutputFormat.Compact.equals(options._outputFormat), options._outputSpace);
      String[] colNames = new String[ContributionsOutputFormat.Compact.equals(options._outputFormat)
              ? dinfo.coefOriginalNames().length + 1 // +1 for bias term
              : beta().length + (_output._dinfo._intercept ? 0 : 1)];
      System.arraycopy(ContributionsOutputFormat.Compact.equals(options._outputFormat)
              ? Arrays.stream(dinfo.coefOriginalNames()).map(name -> {
        if (!dinfo._useAllFactorLevels && name.contains(".") && frame.find(name) == -1) {
          // We can have binary vars encoded as single variable + some contribution in intercept; we need to convert
          // the name of the encoded variable to the original variable name (e.g., sex.female -> sex)
          for (int i = 0; i < name.length(); i++) {
            name = name.substring(0, name.lastIndexOf("."));
            if (frame.find(name) >= 0)
              return name;
          }
        }
        return name;
      }).toArray(String[]::new)
              : _output._coefficient_names, 0, colNames, 0, colNames.length - 1);
      colNames[colNames.length - 1] = "BiasTerm";
      return Scope.untrack(contributions.runAndGetOutput(j, destination_key, colNames));
    } finally {
      Log.info("Finished contributions calculation for " + this._key + "...");
    }
  }

  public static class RegularizationPath extends Iced {
    public double []   _lambdas;
    public double[] _alphas;
    public double []   _explained_deviance_train;
    public double []   _explained_deviance_valid;
    public double [][] _coefficients;
    public double [][] _coefficients_std;
    public String []   _coefficient_names;
    public double [][]   _z_values;
    public double [][]  _p_values;
    public double [][]   _std_errs;
  }

  // go through all submodels, copy lambda, alpha, coefficient values and deviance value\s
  public RegularizationPath getRegularizationPath() { // will be invoked even without lambda_search=true
    RegularizationPath rp = new RegularizationPath();
    rp._coefficient_names = _output._coefficient_names;
    int N = _output._submodels.length;
    int P = _output._dinfo.fullN() + 1;
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal){
      String [] classNames = _output._domains[_output._domains.length-1];
      String [] coefNames = new String[P*_output.nclasses()];
      for(int c = 0; c < _output.nclasses(); ++c){
        for(int i = 0; i < P; ++i)
          coefNames[c*P+i] = _output._coefficient_names[i] + "_" + classNames[c];
      }
      rp._coefficient_names = coefNames;
      P*=_output.nclasses();
    }
    rp._lambdas = new double[N];
    rp._alphas = new double[N];
    rp._coefficients = new double[N][];
    rp._explained_deviance_train = new double[N];
    if (_parms._valid != null)
      rp._explained_deviance_valid = new double[N];
    if (_parms._standardize)
      rp._coefficients_std = new double[N][];
    if (_parms._compute_p_values) {
      rp._z_values = new double[N][];
      rp._p_values = new double[N][];
      rp._std_errs = new double[N][];
    }
    for (int i = 0; i < N; ++i) {
      Submodel sm = _output._submodels[i];
      rp._lambdas[i] = sm.lambda_value;
      rp._alphas[i] = sm.alpha_value;
      rp._coefficients[i] = sm.getBeta(MemoryManager.malloc8d(P));
      if (_parms._standardize) {
        rp._coefficients_std[i] = rp._coefficients[i];
        rp._coefficients[i] = _output._dinfo.denormalizeBeta(rp._coefficients_std[i]);
      }
      if (_parms._compute_p_values) {
        // need to expand vectors to be of size numcols
        rp._z_values[i] = sm.getZValues(MemoryManager.malloc8d(P));
        rp._p_values[i] = sm.pValues(rp._z_values[i], _output._training_metrics.residual_degrees_of_freedom());
        rp._std_errs[i] = sm.stdErr(rp._z_values[i], rp._coefficients[i]);
      }
      rp._explained_deviance_train[i] = 1 - (_output._training_metrics._nobs*sm.devianceTrain)/((GLMMetrics)_output._training_metrics).null_deviance();
      if (rp._explained_deviance_valid != null)
        rp._explained_deviance_valid[i] = 1 - _output._validation_metrics._nobs*sm.devianceValid /((GLMMetrics)_output._validation_metrics).null_deviance();
    }
    return rp;
  }


  @Override
  protected boolean toJavaCheckTooBig() {
    if(beta() != null && beta().length > 10000) {
      Log.warn("toJavaCheckTooBig must be overridden for this model type to render it in the browser");
      return true;
    }
    return false;
  }

  public DataInfo dinfo() { return _output._dinfo; }


  private int rank(double [] ds) {
    int res = 0;
    for(double d:ds)
      if(d != 0) ++res;
    return res;
  }

  @Override public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    if(domain == null && (_parms._family == Family.binomial || _parms._family == Family.quasibinomial 
            || _parms._family == Family.fractionalbinomial))
      domain = binomialClassNames;
    if (_parms._HGLM) {
      String[] domaint = new String[]{"HGLM_" + _parms._family.toString() + "_" + _parms._rand_family[0].toString()};
      return new GLMMetricBuilder(domaint, null, null, 0, true, false, MultinomialAucType.NONE);
    } else
      return new GLMMetricBuilder(domain, _ymu, new GLMWeightsFun(_parms), _output.bestSubmodel().rank(), true, _parms._intercept, _parms._auc_type);
  }

  protected double [] beta_internal(){
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal)
      return flat(_output._global_beta_multinomial);
    return _output._global_beta;
  }
  public double [] beta() { return beta_internal();}
  public double [] beta(double lambda) {
    for(int i = 0 ; i < _output._submodels.length; ++i)
      if(_output._submodels[i].lambda_value == lambda)
        return _output._dinfo.denormalizeBeta(_output._submodels[i].getBeta(MemoryManager.malloc8d(_output._dinfo.fullN()+1)));
    throw new RuntimeException("no such lambda value, lambda = " + lambda);
  }

  public double [] beta_std(double lambda) {
    for(int i = 0 ; i < _output._submodels.length; ++i)
      if(_output._submodels[i].lambda_value == lambda)
        return _output._submodels[i].getBeta(MemoryManager.malloc8d(_output._dinfo.fullN()+1));
    throw new RuntimeException("no such lambda value, lambda = " + lambda);
  }
  public String [] names(){ return _output._names;}

  @Override
  public double deviance(double w, double y, double f) {
    if (w == 0) {
      return 0;
    } else {
      return w*_parms.deviance(y,f);
    }
  }

  @Override
  public double likelihood(double w, double y, double[] f) {
    if (w == 0) {
      return 0;
    } else if (_finalScoring){
      // time-consuming calculation for the final scoring
      return _parms.likelihood(w, y, f);
    } else {
      // optimized calculation for model build
      return w*(_parms.likelihood(y, f[0]));
    }
  }

  public GLMModel addSubmodel(int idx, Submodel sm) { // copy from checkpoint model
    if (_output._submodels != null && _output._submodels.length > idx) {
      _output._submodels[idx] = sm;
    } else {
      assert _output._submodels == null || idx == _output._submodels.length;
      _output._submodels = ArrayUtils.append(_output._submodels, sm);
    }
    _output.setSubmodelIdx(idx, _parms);
    return this;
  }

  public GLMModel updateSubmodel(int idx, Submodel sm) {
    assert sm.lambda_value == _output._submodels[idx].lambda_value && sm.alpha_value == _output._submodels[idx].alpha_value;
    _output._submodels[idx] = sm;
    return this;
  }

  public void update(double [] beta, double devianceTrain, double devianceTest,int iter){
    int id = _output._submodels.length-1;
    _output._submodels[id] = new Submodel(_output._submodels[id].lambda_value,_output._submodels[id].alpha_value,beta,
            iter, devianceTrain, devianceTest, _output._totalBetaLength,
            _output._submodels[id].zValues, _output._submodels[id].dispersionEstimated);
    _output.setSubmodelIdx(id, _parms);
  }

  public void update(double [] beta, double[] ubeta, double devianceTrain, double devianceTest,int iter){
    int id = _output._submodels.length-1;
    Submodel sm = new Submodel(_output._submodels[id].lambda_value,_output._submodels[id].alpha_value,beta,iter,
            devianceTrain, devianceTest, _output._totalBetaLength,
            _output._submodels[id].zValues, _output._submodels[id].dispersionEstimated);
    sm.ubeta = Arrays.copyOf(ubeta, ubeta.length);
    _output._submodels[id] = sm;
    _output.setSubmodelIdx(id, _parms);
  }

  protected GLMModel deepClone(Key<GLMModel> result) {
    GLMModel newModel = IcedUtils.deepCopy(this);
    newModel._key = result;
    // Do not clone model metrics
    newModel._output.clearModelMetrics(false);
    newModel._output._training_metrics = null;
    newModel._output._validation_metrics = null;
    return newModel;
  }

  public static class GLMParameters extends Model.Parameters {
    static final String[] CHECKPOINT_NON_MODIFIABLE_FIELDS = {"_response_column", "_family", "_solver"};
    final static double LOG2PI = Math.log(2 * Math.PI);
    public enum MissingValuesHandling {
      MeanImputation, PlugValues, Skip
    }
    public String algoName() { return "GLM"; }
    public String fullName() { return "Generalized Linear Modeling"; }
    public String javaName() { return GLMModel.class.getName(); }
    @Override public long progressUnits() { return GLM.WORK_TOTAL; }
    public boolean _standardize = true;
    public boolean _useDispersion1 = false; // internal use only, not for users
    public Family _family;
    public Family[] _rand_family;   // for HGLM
    public Link _link;
    public Link[] _rand_link;       // for HGLM
    public Solver _solver = Solver.AUTO;
    public double _tweedie_variance_power;
    public double _tweedie_link_power;
    public double _dispersion_estimated;
    public double _theta; // 1/k and is used by negative binomial distribution only
    public double _invTheta;
    public double [] _alpha;
    public double [] _lambda;
    public double[] _startval;  // for HGLM, initialize fixed and random coefficients (init_u), init_sig_u, init_sig_e
    public boolean _calc_like;
    public int[] _random_columns;
    public int _score_iteration_interval = -1;
    // Has to be Serializable for backwards compatibility (used to be DeepLearningModel.MissingValuesHandling)
    public Serializable _missing_values_handling = MissingValuesHandling.MeanImputation;
    public double _prior = -1;
    public boolean _lambda_search = false;
    public boolean _HGLM = false; // true to enable HGLM
    public boolean _cold_start = false; // start GLM model from scratch if true
    public int _nlambdas = -1;
    public boolean _non_negative = false;
    public double _lambda_min_ratio = -1; // special
    public boolean _use_all_factor_levels = false;
    public int _max_iterations = -1;
    public boolean _intercept = true;
    public double _beta_epsilon = 1e-4;
    public double _dispersion_epsilon = 1e-4; // 1e-4 for gamma, 1e-4 for tweedie
    public int _max_iterations_dispersion = 3000;
    public double _objective_epsilon = -1;  // -1 to set to default
    public double _gradient_epsilon = -1;   // -1 to set to default
    public double _obj_reg = -1;
    public boolean _compute_p_values = false;
    public boolean _remove_collinear_columns = false;
    public String[] _interactions=null;
    public StringPair[] _interaction_pairs=null;
    public boolean _early_stopping = true;
    public Key<Frame> _beta_constraints = null;
    public Key<Frame> _plug_values = null;
    // internal parameter, handle with care. GLM will stop when there is more than this number of active predictors (after strong rule screening)
    public int _max_active_predictors = -1;
    public boolean _stdOverride; // standardization override by beta constraints
    final static NormalDistribution _dprobit = new NormalDistribution(0,1);  // get the normal distribution
    public GLMType _glmType = GLMType.glm;
    public boolean _generate_scoring_history = false; // if true, will generate scoring history but will slow algo down
    public DispersionMethod _dispersion_parameter_method = DispersionMethod.pearson;
    public double _init_dispersion_parameter = 1.0;
    public boolean _fix_dispersion_parameter = false;
    public boolean _build_null_model = false;
    public boolean _generate_variable_inflation_factors = false; // if enabled, will generate variable_inflation_factors for numeric predictors
    public double _tweedie_epsilon = 8e-17; // paper suggests 8e-17
    public boolean _fix_tweedie_variance_power = true;
    public int _max_series_index = 5000;
    public boolean _debugTDispersionOnly = false;  // debug only and will slow down model building
    public double _dispersion_learning_rate = 0.5;
    public Influence _influence;  // if set to dfbetas will calculate the difference of betas obtained from including and excluding a data row
    public boolean _keepBetaDiffVar = false;  // if true, will keep the frame generating the beta without from i and the variance estimation
    
    public void validate(GLM glm) {
      if (_remove_collinear_columns) {
        if (!(Solver.IRLSM.equals(_solver) || Solver.AUTO.equals(_solver)))
          glm.warn("remove_collinear_columns", "remove_collinear_columns only works when IRLSM (or " +
                  "AUTO which will default to IRLSM when remove_collinear_columns=true) is chosen as the solver.  " +
                  "Otherwise, remove_collinear_columns is not enabled.");

        if (_lambda_search) {
          glm.warn("remove_collinear_columns", "remove_collinear_columns should only be used with " +
                  "no regularization, i.e. lambda=0.0.  It is used improperly here with lambda_search.  " +
                  "Please disable lambda_search and set lambda=0.");
        } else if (_lambda != null) {
          boolean nonZeroLambda = Arrays.stream(_lambda).sum() > 0;
          if (nonZeroLambda)
            glm.warn("remove_collinear_columns", "remove_collinear_columns should only be used with " +
                    "no regularization, i.e. lambda=0.0.  It is used improperly here.  Please set lambda=0.");
        }
      }
      if (_solver.equals(Solver.COORDINATE_DESCENT_NAIVE) && _family.equals(Family.multinomial))
        throw H2O.unimpl("Naive coordinate descent is not supported for multinomial.");
      if ((_lambda != null) && _lambda_search)
        glm.warn("lambda_search", "disabled when user specified any lambda value(s).");
      if(_alpha != null && (1 < _alpha[0] || _alpha[0] < 0))
        glm.error("_alpha","alpha parameter must from (inclusive) [0,1] range");
      if(_compute_p_values && _solver != Solver.AUTO && _solver != Solver.IRLSM)
        glm.error("_compute_p_values","P values can only be computed with IRLSM solver, go solver = " + _solver);
      if(_compute_p_values && (_family == Family.multinomial || _family==Family.ordinal))
        glm.error("_compute_p_values","P values are currently not supported for " +
                "family=multinomial or ordinal");
      if(_compute_p_values && _non_negative)
        glm.error("_compute_p_values","P values are currently not supported for " +
                "family=multinomial or ordinal");
      if(_weights_column != null && _offset_column != null && _weights_column.equals(_offset_column))
        glm.error("_offset_column", "Offset must be different from weights");
      if(_alpha != null && (_alpha[0] < 0 || _alpha[0] > 1))
        glm.error("_alpha", "Alpha value must be between 0 and 1");
      if(_lambda != null && _lambda[0] < 0)
        glm.error("_lambda", "Lambda value must be >= 0");
      if(_obj_reg != -1 && _obj_reg <= 0)
        glm.error("obj_reg","Must be positive or -1 for default");
      if(_prior != -1 && _prior <= 0 || _prior >= 1)
        glm.error("_prior","Prior must be in (exclusive) range (0,1)");
      if(_prior != -1 && _family != Family.binomial)
        glm.error("_prior","Prior is only allowed with family = binomial.");
      if(_family != Family.tweedie) {
        glm.hide("_tweedie_variance_power","Only applicable with Tweedie family");
        glm.hide("_tweedie_link_power","Only applicable with Tweedie family");
      }
      if(_family != Family.negativebinomial) {
        glm.hide("_theta","Only applicable with Negative Binomial family");
      }
      if(_remove_collinear_columns && !_intercept)
        glm.error("_intercept","Remove colinear columns option is currently not supported without intercept");
      if(_beta_constraints != null) {
        if(_family == Family.multinomial || _family==Family.ordinal)
          glm.error("beta_constraints","beta constraints are not supported for " +
                  "family = multionomial or ordinal");
        Frame f = _beta_constraints.get();
        if(f == null) glm.error("beta_constraints","Missing frame for beta constraints");
        Vec v = f.vec("names");
        if(v == null)glm.error("beta_constraints","Beta constraints parameter must have names column with valid coefficient names");
        // todo: check the coefficient names
        v = f.vec("upper_bounds");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","upper_bounds must be numeric if present");v = f.vec("upper_bounds");
        v = f.vec("lower_bounds");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","lower_bounds must be numeric if present");
        v = f.vec("beta_given");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","beta_given must be numeric if present");v = f.vec("upper_bounds");
        v = f.vec("beta_start");
        if(v != null && !v.isNumeric())
          glm.error("beta_constraints","beta_start must be numeric if present");
      }
      if(!_lambda_search) {
        glm.hide("_lambda_min_ratio", "only applies if lambda search is on.");
        glm.hide("_nlambdas", "only applies if lambda search is on.");
        glm.hide("_early_stopping","only applies if lambda search is on.");
      }
      if (_family==Family.ordinal) {
        if (_intercept == false)
          glm.error("Ordinal regression", "must have intercepts.  set _intercept to true.");
        if (!(_solver.equals(Solver.AUTO) || _solver.equals(Solver.GRADIENT_DESCENT_SQERR) || _solver.equals(Solver.GRADIENT_DESCENT_LH)))
          glm.error("Ordinal regression","Ordinal regression only supports gradient descend.  " +
                  "Do not set Solver or set Solver to auto, GRADIENT_DESCENT_LH or GRADIENT_DESCENT_SQERR.");
        if (_lambda_search)
          glm.error("ordinal regression", "Ordinal regression do not support lambda search.");
      }
      if (_HGLM) {  // check correct parameter settings for HGLM
        if (_random_columns==null)
          throw new IllegalArgumentException("Need to specify the random component columns for HGLM.");
        if (!(_random_columns.length == 1))
          throw new IllegalArgumentException("HGLM only supports ONE random component for now.");
        if (!(_rand_family==null) && !(_rand_family.length==_random_columns.length))
          throw new IllegalArgumentException("HGLM _rand_family: must have the same length as random_columns.");
        if (!(_rand_link==null) && !(_rand_link.length==_random_columns.length))
          throw new IllegalArgumentException("HGLM _rand_link: must have the same length as random_columns.");   
        if (!_family.equals(Family.gaussian))
          throw new IllegalArgumentException("HGLM only supports Gaussian distributions for now.");
        if (!(_rand_family==null)) {
          for (Family fam : _rand_family) {
            if (!fam.equals(Family.gaussian))
              throw new IllegalArgumentException("HGLM only supports Gaussian distributions for now.");
          }
        }
        if (!(_rand_link==null)) {
          for (Link lin : _rand_link) {
            if (!lin.equals(Link.identity) && !lin.equals(Link.family_default))
            throw new IllegalArgumentException("HGLM only supports identity link functions for now.");
          }
        }
        if (!(_link.equals(Link.family_default)) && !(_link.equals(Link.identity)))
          throw new IllegalArgumentException("HGLM only supports identity link functions for now.");
        if (_lambda_search)
          throw new IllegalArgumentException("HGLM does not allow lambda search.  Set it to False/FALSE/false to disable it.");
        if (_nfolds > 1)
          throw new IllegalArgumentException("HGLM does not allow cross-validation.");
        if (_valid != null)
          throw new IllegalArgumentException("HGLM does not allow validation.");
        _glmType = GLMType.hglm;
      }
      if(_link != Link.family_default) { // check we have compatible link
        switch (_family) {
          case AUTO:
            if (_link != Link.family_default & _link != Link.identity & _link != Link.log & _link != Link.inverse 
                    & _link != Link.logit & _link != Link.multinomial)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only family_default, identity, log, inverse, logit and multinomial are allowed for family=AUTO");
            break;
          case gaussian:
            if (_link != Link.identity && _link != Link.log && _link != Link.inverse)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only identity, log and inverse links are allowed for family=gaussian.");
            break;
          case quasibinomial:
          case binomial:
          case fractionalbinomial:
            if (_link != Link.logit) // fixme: R also allows log, but it's not clear when can be applied and what should we do in case the predictions are outside of 0/1.
              throw new IllegalArgumentException("Incompatible link function for selected family. Only logit is allowed for family=" + _family + ". Got " + _link);
            break;
          case poisson:
          case negativebinomial:  
            if (_link != Link.log && _link != Link.identity)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only log and " +
                      "identity links are allowed for family=poisson and family=negbinomimal.");
            break;
          case gamma:
            if (_link != Link.inverse && _link != Link.log && _link != Link.identity)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only inverse, log and identity links are allowed for family=gamma.");
            break;
          case tweedie:
            if (_link != Link.tweedie)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only tweedie link allowed for family=tweedie.");
            break;
          case multinomial:
            if(_link != Link.multinomial)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only multinomial link allowed for family=multinomial.");
            break;
          case ordinal:
            if (_link != Link.ologit && _link!=Link.oprobit && _link!=Link.ologlog)
              throw new IllegalArgumentException("Incompatible link function for selected family. Only ologit, oprobit or ologlog links allowed for family=ordinal.");
            break;
          default:
            H2O.fail();
        }
      }
      if (_missing_values_handling != null) {
        if (!(_missing_values_handling instanceof DeepLearningModel.DeepLearningParameters.MissingValuesHandling) &&
                !(_missing_values_handling instanceof MissingValuesHandling)) {
          throw new IllegalArgumentException("Missing values handling should be specified as an instance of " + MissingValuesHandling.class.getName());
        }
      }
    }
    
    public GLMParameters() {
      this(Family.AUTO, Link.family_default);
      assert _link == Link.family_default;
      _stopping_rounds = 0; // early-stopping is disabled by default
    }

    public GLMParameters(Family f){this(f,f.defaultLink);}
    public GLMParameters(Family f, Link l){this(f,l, null, null, 0, 1);}
    public GLMParameters(Family f, Link l, double[] lambda, double[] alpha, double twVar, double twLnk) {
      this(f,l,lambda,alpha,twVar,twLnk,null);
    }

    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha, double twVar, double twLnk, String[] interactions) {
      this(f,l,lambda,alpha,twVar,twLnk,interactions,GLMTask.EPS);
    }
    

    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha, double twVar, double twLnk, 
                         String[] interactions, double theta){
      this(f,l,lambda,alpha,twVar,twLnk,interactions, theta, Double.NaN);
    }

    public GLMParameters(Family f, Link l, double [] lambda, double [] alpha, double twVar, double twLnk,
                         String[] interactions, double theta, double dispersion_estimated){
      this._lambda = lambda;
      this._alpha = alpha;
      this._tweedie_variance_power = twVar;
      this._tweedie_link_power = twLnk;
      _interactions=interactions;
      _family = f;
      _link = l;
      this._theta=theta;
      this._invTheta = 1.0/theta;
      this._dispersion_estimated = Double.isNaN(dispersion_estimated) ? _init_dispersion_parameter : dispersion_estimated;
    }

    public final double variance(double mu){
      switch(_family) {
        case gaussian:
          return 1;
        case binomial:
        case multinomial:
        case ordinal:
        case quasibinomial:
        case fractionalbinomial:
          return mu * (1 - mu);
        case poisson:
          return mu;
        case gamma:
          return mu * mu;
        case tweedie:
          return Math.pow(mu, _tweedie_variance_power);
        default:
          throw new RuntimeException("unknown family Id " + this._family);
      }
    }

    public final boolean canonical(){
      switch(_family){
        case gaussian:
          return _link == Link.identity;
        case binomial:
        case quasibinomial:
        case fractionalbinomial:
          return _link == Link.logit;
        case poisson:
          return _link == Link.log;
        case gamma:
          return _link == Link.inverse;
//        case tweedie:
//          return false;
        default:
          throw H2O.unimpl();
      }
    }

    public final double deviance(double yr, double ym){
      double y1 = yr == 0?.1:yr;
      switch(_family){
        case gaussian:
          return (yr - ym) * (yr - ym);
        case quasibinomial:
        case binomial:
        case fractionalbinomial:
          return 2 * ((y_log_y(yr, ym)) + y_log_y(1 - yr, 1 - ym));
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case negativebinomial:
          return (yr==0||ym==0)?0:2*((_invTheta+yr)*Math.log((1+_theta*ym)/(1+_theta*yr))+yr*Math.log(yr/ym));        
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          if (DispersionMethod.ml.equals(_dispersion_parameter_method)) {
            return TweedieEstimator.deviance(yr, ym, _tweedie_variance_power);
          }
          double theta = _tweedie_variance_power == 1
            ?Math.log(y1/ym)
            :(Math.pow(y1,1.-_tweedie_variance_power) - Math.pow(ym,1 - _tweedie_variance_power))/(1-_tweedie_variance_power);
          double kappa = _tweedie_variance_power == 2
            ?Math.log(y1/ym)
            :(Math.pow(yr,2-_tweedie_variance_power) - Math.pow(ym,2-_tweedie_variance_power))/(2 - _tweedie_variance_power);
          return 2 * (yr * theta - kappa);
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }
    public final double deviance(float yr, float ym){
     return deviance((double)yr,(double)ym);
    }

    // likelihood calculation used for the model building
    public final double likelihood(double yr, double ym){
      if (_family.equals(Family.negativebinomial)) {
        return ((yr>0 && ym>0)?
                (-GLMTask.sumOper(yr, _invTheta, 0)+_invTheta*Math.log(1+_theta*ym)-yr*Math.log(ym)-
                        yr*Math.log(_theta)+yr*Math.log(1+_theta*ym)):
                ((yr==0 && ym>0)?(_invTheta*Math.log(1+_theta*ym)):0)); // with everything
      }  else if (Family.tweedie.equals(_family) && DispersionMethod.ml.equals(_dispersion_parameter_method) && !_fix_tweedie_variance_power) {
        return -TweedieEstimator.logLikelihood(yr, ym, _tweedie_variance_power, _dispersion_estimated);
      }  else
        return .5 * deviance(yr,ym);
    }

    // more time-consuming likelihood calculation used for the final scoring
    public final double likelihood(double w, double yr, double[] ym) {
      double prediction = ym[0];
      double probabilityOf1;
      switch (_family) {
        case gaussian:
          return -.5 * (w * Math.pow(yr - prediction , 2) / _dispersion_estimated 
                  + log(_dispersion_estimated / w) + LOG2PI);
        case binomial:
          // if probability is not given, then it is 1.0 if 1 is predicted and 0.0 if 0 is predicted
          probabilityOf1 = ym.length > 1 ? ym[2] : ym[0]; // probability of 1 equals prediction
          return w * (yr * log(probabilityOf1) + (1-yr) * log(1 - probabilityOf1));
        case quasibinomial:
          // if probability is not given, then it is 1.0 if 1 is predicted and 0.0 if 0 is predicted
          probabilityOf1 = ym.length > 1 ? ym[2] : ym[0]; // probability of 1 equals prediction
          if (yr == prediction)
            return 0;
          else if (prediction > 1) // check what are possible values?
            return -w * (yr * log(probabilityOf1));
          else
            return -w * (yr * log(probabilityOf1) + (1 - yr) * log(1 - probabilityOf1));
        case fractionalbinomial:
          // if probability is not given, then it is 1.0 if 1 is predicted and 0.0 if 0 is predicted
          probabilityOf1 = ym.length > 1 ? ym[2] : ym[0]; // probability of 1 equals prediction
          if (yr == prediction)
            return 0;
          return w * ((MathUtils.y_log_y(yr, probabilityOf1)) + MathUtils.y_log_y(1 - yr, 1 - probabilityOf1));
        case poisson:
          return w * (yr * log(prediction) - prediction - Gamma.logGamma(yr + 1)); // gamma(n) = (n-1)!
        case negativebinomial:
          // the estimated dispersion parameter is theta. The likelihood formula requires k. Theta=1/k.
          double invThetaEstimated = 1 / _dispersion_estimated;
          return yr * log(invThetaEstimated * prediction / w) - (yr + w/invThetaEstimated) * log(1 + invThetaEstimated * prediction / w) 
                  + log(Gamma.gamma(yr + w / invThetaEstimated) / (Gamma.gamma(yr + 1) * Gamma.gamma(w / invThetaEstimated)));
        case gamma:
          double invPhiEst = 1 / _dispersion_estimated;
          return w * invPhiEst * log(w * yr * invPhiEst / prediction) - w * yr * invPhiEst / prediction 
                  - log(yr) - Gamma.logGamma(w * invPhiEst);
        case tweedie:
          return -TweedieEstimator.logLikelihood(yr, ym[0], _tweedie_variance_power, _dispersion_estimated);
        case multinomial:
          // if probability is not given, then it is 1.0 if prediction equals to the real y and 0 othervice
          double predictedProbabilityOfActualClass = ym.length > 1 ? ym[(int) yr + 1] : (prediction == yr ? 1.0 : 0.0);
          return w * log(predictedProbabilityOfActualClass);
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }

    public final double linkDeriv(double x) { // note: compute an inverse of what R does
      switch(_link) {
        case ologit:
        case logit:
//        case multinomial:
          double div = (x * (1 - x));
          if(div < _EPS) return _OneOEPS; // avoid numerical instability
          return 1.0 / div;
        case identity:
          return 1;
        case log:
          return 1.0 / x;
        case inverse:
          return -1.0 / (x * x);
        case ologlog:
          double oneMx = 1.0-x;
          double divsor = -1.0*oneMx*Math.log(oneMx);
          return (divsor<_EPS)?_OneOEPS:(1.0/divsor);
        case tweedie:
//          double res = _tweedie_link_power == 0
//            ?Math.max(2e-16,Math.exp(x))
//            // (1/lambda) * eta^(1/lambda - 1)
//            :(1.0/_tweedie_link_power) * Math.pow(link(x), 1.0/_tweedie_link_power - 1.0);

          return _tweedie_link_power == 0
            ?1.0/Math.max(2e-16,x)
            :_tweedie_link_power * Math.pow(x,_tweedie_link_power-1);
        default:
          throw H2O.unimpl();
      }
    }

    public final double randLinkInv(double x, int index) {
      switch(_rand_link[index]) {
//        case multinomial: // should not be used
        case identity:
          return x;
        case ologlog:
          return 1.0-Math.exp(-1.0*Math.exp(x));
        case oprobit:
          return _dprobit.cumulativeProbability(x);
        case ologit:
        case logit:
          return 1.0 / (Math.exp(-x) + 1.0);
        case log:
          return Math.exp(x);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return 1.0 / xx;
        case tweedie:
          return _tweedie_link_power == 0
                  ?Math.max(2e-16,Math.exp(x))
                  :Math.pow(x, 1/ _tweedie_link_power);
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }
    
    public final double linkInv(double x) {
      switch(_link) {
//        case multinomial: // should not be used
        case identity:
          return x;
        case ologlog:
          return 1.0-Math.exp(-1.0*Math.exp(x));
        case oprobit:
          return _dprobit.cumulativeProbability(x);
        case ologit:
        case logit:
          return 1.0 / (Math.exp(-x) + 1.0);
        case log:
          return Math.exp(x);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return 1.0 / xx;
        case tweedie:
          return _tweedie_link_power == 0
            ? Math.max(2e-16, Math.exp(x))
            : Math.pow(x, 1/ _tweedie_link_power);
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }
    
    // supported families
    public enum Family {
      AUTO(Link.family_default), gaussian(Link.identity), binomial(Link.logit), fractionalbinomial(Link.logit), quasibinomial(Link.logit),poisson(Link.log),
      gamma(Link.inverse), multinomial(Link.multinomial), tweedie(Link.tweedie), ordinal(Link.ologit), 
      negativebinomial(Link.log);
      public final Link defaultLink;
      Family(Link link){defaultLink = link;}
    }
    
    public enum DispersionMethod {pearson, ml, deviance} // methods used to estimate dispersion parameter, ML = maximum likelhood
    public static enum GLMType {glm, gam, hglm} // special functions are performed depending on GLMType.  Internal use
    public static enum Link {family_default, identity, logit, log, inverse, tweedie, multinomial, ologit, oprobit, ologlog}
    public static enum Influence {dfbetas};

    public static enum Solver {AUTO, IRLSM, L_BFGS, COORDINATE_DESCENT_NAIVE, COORDINATE_DESCENT, GRADIENT_DESCENT_LH, GRADIENT_DESCENT_SQERR}

    // helper function
    static final double y_log_y(double y, double mu) {
      if(y == 0)return 0;
      if(mu < Double.MIN_NORMAL) mu = Double.MIN_NORMAL;
      return y * Math.log(y / mu);
    }

    public InteractionSpec interactionSpec() {
      return InteractionSpec.create(_interactions, _interaction_pairs);
    }

    public MissingValuesHandling missingValuesHandling() {
      if (_missing_values_handling instanceof MissingValuesHandling)
        return (MissingValuesHandling) _missing_values_handling;
      assert _missing_values_handling instanceof DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
      switch ((DeepLearningModel.DeepLearningParameters.MissingValuesHandling) _missing_values_handling) {
        case MeanImputation:
          return MissingValuesHandling.MeanImputation;
        case Skip:
          return MissingValuesHandling.Skip;
        default:
          throw new IllegalStateException("Unsupported missing values handling value: " + _missing_values_handling);
      }
    }

    public boolean imputeMissing() {
      return missingValuesHandling() == MissingValuesHandling.MeanImputation || 
              missingValuesHandling() == MissingValuesHandling.PlugValues; 
    }
    
    public DataInfo.Imputer makeImputer() {
      if (missingValuesHandling() == MissingValuesHandling.PlugValues) {
        if (_plug_values == null || _plug_values.get() == null) {
          throw new IllegalStateException("Plug values frame needs to be specified when Missing Value Handling = PlugValues.");
        }
        return new GLM.PlugValuesImputer(_plug_values.get());
      } else { // mean/mode imputation and skip (even skip needs an imputer right now! PUBDEV-6809)
        return new DataInfo.MeanImputer();
      }
    }

    @Override
    public void setDistributionFamily(DistributionFamily distributionFamily) {
        _family = distributionToFamily(distributionFamily);
        _link = Link.family_default;
    }

    @Override
    public DistributionFamily getDistributionFamily() {
      return familyToDistribution(_family);
    }
    
    public void updateTweedieParams(double tweedieVariancePower, double tweedieLinkPower, double dispersion){
      _tweedie_variance_power = tweedieVariancePower;
      _tweedie_link_power = tweedieLinkPower;
      _dispersion_estimated = dispersion;
      _init_dispersion_parameter = dispersion;
    }
  } // GLMParameters

  public static class GLMWeights {
    public double mu = 0;
    public double w = 1;
    public double z = 0;
    public double l = 0;
    public double dev = Double.NaN;
  }
  public static class GLMWeightsFun extends Iced {
    final public Family _family;
    final Link _link;
    final double _var_power;
    final double _link_power;
    final double _oneOoneMinusVarPower;
    final double _oneOtwoMinusVarPower;
    final double _oneMinusVarPower;
    final double _twoMinusVarPower;
    final double _oneOLinkPower;
    final double _oneOLinkPowerSquare;
    double _theta;  // used by negative binomial, 0 < _theta <= 1
    double _invTheta;
    double _dispersion;
    double _oneOeta;
    double _oneOetaSquare;
    boolean _varPowerEstimation;

    final NormalDistribution _dprobit = new NormalDistribution(0,1);  // get the normal distribution
    
    public GLMWeightsFun(GLMParameters parms) {
      this(parms._family,parms._link, parms._tweedie_variance_power,
              parms._tweedie_link_power, parms._theta, parms._init_dispersion_parameter,
              GLMParameters.DispersionMethod.ml.equals(parms._dispersion_parameter_method) && !parms._fix_tweedie_variance_power);
    }

    public GLMWeightsFun(Family fam, Link link, double var_power, double link_power, double theta, double dispersion, boolean varPowerEstimation) {
      _family = fam;
      _link = link;
      _var_power = var_power;
      _link_power = link_power;
      _oneMinusVarPower = 1-_var_power;
      _twoMinusVarPower = 2-_var_power;
      _oneOoneMinusVarPower = _var_power==1?1:1.0/(1-_var_power);
      _oneOtwoMinusVarPower = _var_power==2?1:1.0/(2-_var_power);
      _oneOLinkPower = 1.0/_link_power;
      _oneOLinkPowerSquare = _oneOLinkPower*_oneOLinkPower;
      _theta = theta;
      _invTheta = 1/theta;
      _dispersion = dispersion;
      _varPowerEstimation = varPowerEstimation;
    }

    /***
     * Given the estimated model output x, we want to find the linear part which is transpose(beta)*p+intercept if
     * beta does not contain the intercept.
     */
    public final double link(double x) {
      switch(_link) {
        case identity:
          return x;
        case ologit:  // note: x here is the CDF
        case logit:
          assert 0 <= x && x <= 1:"x out of bounds, expected <0,1> range, got " + x;
          return Math.log(x / (1 - x));
        case ologlog:
          return Math.log(-1.0*Math.log(1-x));  // x here is CDF
        case oprobit: // x is normal with 0 mean and variance 1
          return _dprobit.inverseCumulativeProbability(x);
        case multinomial:
        case log:
          return Math.log(x);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return 1.0 / xx;
        case tweedie:
          return _link_power == 0?Math.log(x):Math.pow(x, _link_power);
        default:
          throw new RuntimeException("unknown link function " + this);
      }
    }
    
    public final double linkInvDeriv(double x) {
      switch(_link) {
        case identity:
          return 1;
        case logit:
          double g = Math.exp(-x);
          double gg = (g + 1) * (g + 1);
          return g / gg;
        case ologit:
          return (x-x*x);
        case log:
          return Math.max(x, Double.MIN_NORMAL);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return -1 / (xx * xx);
        case tweedie:
          return _link_power==0?Math.max(x, Double.MIN_NORMAL):x*_oneOLinkPower*_oneOeta;
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }


    public final double linkInvDeriv2(double x) {
      switch(_link) {
        case identity:
          return 0;
        case log:
          return Math.max(x, Double.MIN_NORMAL);
        case tweedie:
          return _link_power==0?Math.max(x, Double.MIN_NORMAL):x*_oneOLinkPower*(_oneOLinkPower-1)*_oneOetaSquare;
        default:
          throw new RuntimeException("unexpected link function id  " + this);
      }
    }


    // calculate the derivative of the link function
    public final double linkDeriv(double x) { // note: compute an inverse of what R does
      switch(_link) {
        case ologit:  // note, x is CDF not PDF
        case logit:
//        case multinomial:
          double div = (x * (1 - x));
          if(div < _EPS) return _OneOEPS; // avoid numerical instability
          return 1.0 / div;
        case ologlog:
          double oneMx = 1.0-x;
          double divsor = -1.0*oneMx*Math.log(oneMx);
          return (divsor<_EPS)?_OneOEPS:(1.0/divsor);
        case identity:
          return 1;
        case log:
          return 1.0 / x;
        case inverse:
          return -1.0 / (x * x);
        case tweedie:
          return _link_power == 0
            ?1.0/Math.max(2e-16,x)
            :_link_power * Math.pow(x,_link_power-1);
        default:
          throw H2O.unimpl();
      }
    }

    /***
     * Given the linear combination transpose(beta)*p+intercept (if
     * beta does not contain the intercept), this method will provide the estimated model output.
     */
    public final double linkInv(double x) {
      switch(_link) {
        case ologlog:
          return 1.0-Math.exp(-1.0*Math.exp(x));
        case oprobit:
          return _dprobit.cumulativeProbability(x);
        case identity:
          return x;
        case ologit:
        case logit:
          return 1.0 / (Math.exp(-x) + 1.0);
        case log:
          return Math.exp(x);
        case inverse:
          double xx = (x < 0) ? Math.min(-1e-5, x) : Math.max(1e-5, x);
          return 1.0 / xx;
        case tweedie:
          return _link_power == 0
            ?Math.max(2e-16,Math.exp(x))
            :Math.pow(x, _oneOLinkPower);
        default:
          throw new RuntimeException("unexpected link function id  " + _link);
      }
    }
    public final double variance(double mu){
      switch(_family) {
        case gaussian:
          return 1;
        case quasibinomial:
        case binomial:
        case fractionalbinomial:
          double res = mu * (1 - mu);
          return res < _EPS?_EPS:res;
        case poisson:
          return mu;
        case negativebinomial:
          return (mu+mu*mu*_theta);
        case gamma:
          return mu * mu;
        case tweedie:
          return Math.pow(mu,_var_power);
        default:
          throw new RuntimeException("unknown family Id " + this._family);
      }
    }

    public final double deviance(double yr, double ym){
      double y1 = yr == 0?0.1:yr; // this must be kept as 0.1, otherwise, answer differs from R.
      switch(_family){
        case gaussian:
          return (yr - ym) * (yr - ym);
        case quasibinomial:
          if(yr == ym) return 0;
          if(ym > 1) return -2 * (yr*Math.log(ym));
          double res = -2 * (yr*Math.log(ym) + (1-yr)*Math.log(1-ym));
          return res;
        case binomial:
        case fractionalbinomial:
          return 2 * ((MathUtils.y_log_y(yr, ym)) + MathUtils.y_log_y(1 - yr, 1 - ym));
        case poisson:
          if( yr == 0 ) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case negativebinomial:
          if( yr == 0 && ym <= 0 ) return 0;
          if( yr == 0 ) return 2 * _invTheta * Math.log(1 + _theta * ym);
          return 2*((_invTheta+yr)*Math.log((1+_theta*ym)/(1+_theta*yr))+yr*Math.log(yr/ym));
        case gamma:
          if( yr == 0 ) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          if (_varPowerEstimation)
            return TweedieEstimator.deviance(yr, ym, _var_power);
          double val;
          if (_var_power==1) {
            val = yr*Math.log(y1/ym)-(yr-ym);
          } else if (_var_power==2) {
            val = yr*(1/ym-1/y1)-Math.log(y1/ym);
          } else {
            val = (yr==0?0:yr*_oneOoneMinusVarPower*(Math.pow(yr,_oneMinusVarPower)-Math.pow(ym, _oneMinusVarPower)))-
                    (Math.pow(yr,_twoMinusVarPower)-Math.pow(ym, _twoMinusVarPower))*_oneOtwoMinusVarPower;
          }
          return 2 * val;
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }
    public final double deviance(float yr, float ym){
      return deviance((double)yr,(double)ym);
    }

    public final void likelihoodAndDeviance(double yr, GLMWeights x, double w) {
      double ym = x.mu;
      switch (_family) {
        case gaussian:
          x.dev = w * (yr - ym) * (yr - ym);
          x.l =  .5 * x.dev;
          break;
        case quasibinomial:
          if(yr == ym) x.l = 0;
          else if (ym > 1) x.l = -(yr*Math.log(ym));
          else x.l = - (yr*Math.log(ym) + (1-yr)*Math.log(1-ym));
          x.dev = 2*x.l;
          break;
        case binomial:
        case fractionalbinomial:
          x.l = ym == yr?0:w*((MathUtils.y_log_y(yr, ym)) + MathUtils.y_log_y(1 - yr, 1 - ym));
          x.dev = 2*x.l;
          break;
        case poisson:
        case gamma:
        case tweedie:
          x.dev = w*deviance(yr,ym);
          x.l = likelihood(w, yr, ym); 
          break;
        case negativebinomial:
          x.dev = w*deviance(yr,ym); // CHECKED-log/CHECKED-identity
          x.l = w*likelihood(yr,ym); // CHECKED-log/CHECKED-identity
          break;
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }
    
    public final double likelihood(double w, double yr, double ym) {
      if (w==0)
        return 0;
      return w*likelihood(yr, ym);
    }
    
    public final double likelihood(double yr, double ym) {
      switch (_family) {
        case gaussian:
          return .5 * (yr - ym) * (yr - ym);
        case binomial:
        case quasibinomial:
        case fractionalbinomial:
          if (yr == ym) return 0;
          return .5 * deviance(yr, ym);
        case poisson:
          if (yr == 0) return 2 * ym;
          return 2 * ((yr * Math.log(yr / ym)) - (yr - ym));
        case negativebinomial:
          return ((yr>0 && ym>0)?
                  (-GLMTask.sumOper(yr, _invTheta, 0)+_invTheta*Math.log(1+_theta*ym)-yr*Math.log(ym)-
                          yr*Math.log(_theta)+yr*Math.log(1+_theta*ym)):
                  ((yr==0 && ym>0)?(_invTheta*Math.log(1+_theta*ym)):0)); // with everything
        case gamma:
          if (yr == 0) return -2;
          return -2 * (Math.log(yr / ym) - (yr - ym) / ym);
        case tweedie:
          if (_varPowerEstimation)
            return -TweedieEstimator.logLikelihood(yr, ym, _var_power, _dispersion);
         //  we ignore the a(y,phi,p) term in the likelihood calculation here since we are not optimizing over them
          double temp = 0;
          if (_var_power==1) {
            temp = Math.pow(ym, _twoMinusVarPower)*_oneOtwoMinusVarPower-yr*Math.log(ym);
          } else if (_var_power==2) {
            temp = Math.log(ym)-yr*Math.pow(ym, _oneMinusVarPower)*_oneOoneMinusVarPower;
          } else {
            temp = Math.pow(ym, _twoMinusVarPower)*_oneOtwoMinusVarPower-yr*Math.pow(ym, _oneMinusVarPower)*_oneOoneMinusVarPower;
          }
          return temp; // ignored the a(y,phi,p) term as it is a constant for us
        default:
          throw new RuntimeException("unknown family " + _family);
      }
    }

    public GLMWeights computeWeights(double y, double eta, double off, double w, GLMWeights x) {
      double etaOff = eta + off;
      x.mu = linkInv(etaOff);
      double var = variance(x.mu);//Math.max(1e-5, variance(x.mu)); // avoid numerical problems with 0 variance
      double d = linkDeriv(x.mu);
      if (_family.equals(Family.negativebinomial)) {
        double invSum = 1.0/(1+_theta*x.mu);
        double d2 = linkInvDeriv(x.mu);
        if (y>0 && (x.mu>0)) {
          double sumr = 1.0+_theta*y;
          d = (y/(x.mu*x.mu)-_theta*sumr*invSum*invSum) * d2 * d2 + (sumr*invSum-y/x.mu) * linkInvDeriv2(x.mu); //CHECKED-log/CHECKED-identity
          x.w = w*d;
          x.z = eta + (y-x.mu) *invSum * d2/(d*x.mu); // CHECKED-identity
        } else if (y==0 && x.mu > 0) {
          d = linkInvDeriv2(x.mu)*invSum-_theta*invSum*invSum*d2*d2; // CHECKED
          //d = Math.min(1e10, Math.max(1e-10, d));
          x.w = w*d;
          x.z = eta - invSum*d2/d;
        } else {
          x.w = 0;
          x.z = 0;
        }
      } else if (_family.equals(Family.tweedie)) {  // here, the x.z is actually wz
        double oneOxmu = x.mu==0?_OneOEPS:1.0/x.mu;
        double oneOxmuSquare = oneOxmu*oneOxmu;
        _oneOeta = etaOff==0?_OneOEPS:1.0/etaOff; // use etaOff here since the derivative is wrt to eta+offset
        _oneOetaSquare = _oneOeta*_oneOeta;
        double diffOneSquare = linkInvDeriv(x.mu)*linkInvDeriv(x.mu);
        double xmuPowMP = Math.pow(x.mu, -_var_power);
        if (_var_power==1) {
          x.w = y*oneOxmuSquare*diffOneSquare-(y*oneOxmu-1)*linkInvDeriv2(x.mu);
          x.z = (x.w*eta + (y*oneOxmu-1)*linkInvDeriv(x.mu))*w;
        } else if (_var_power == 2) {
          x.w = (oneOxmu-y*xmuPowMP)*linkInvDeriv2(x.mu)+ (y*2*Math.pow(x.mu, -3)-oneOxmuSquare)*diffOneSquare;
          x.z = (x.w*eta+(y*oneOxmuSquare-oneOxmu)*linkInvDeriv(x.mu))*w;
        } else {
          x.w = (_var_power*y*Math.pow(x.mu, -_var_power-1)+_oneMinusVarPower*xmuPowMP)*diffOneSquare-
                  (y*xmuPowMP-Math.pow(x.mu, _oneMinusVarPower))*linkInvDeriv2(x.mu);
          x.z = (x.w*eta+(y*Math.pow(x.mu, -_var_power)-Math.pow(x.mu, _oneMinusVarPower))*linkInvDeriv(x.mu))*w;
        }
        x.w *= w;
      } else {
        x.w = w / (var * d * d);  // formula did not quite work with negative binomial
        x.z = eta + (y - x.mu) * d; // only eta and no r.offset should be applied.  I derived this.
      }
      likelihoodAndDeviance(y,x,w);
      return x;
    }
  }

  public static class Submodel extends Iced {
    public final double lambda_value;
    public final double alpha_value;
    public final int    iteration;
    public final double devianceTrain;
    public final double devianceValid;
    public final int    [] idxs;
    public final double [] beta;
    public double[] ubeta;  // store HGLM random coefficients
    public double _trainTheta;
    public double[] zValues;
    public boolean dispersionEstimated;


    public double [] getBeta(double [] beta) {
      if(idxs != null){
        for(int i = 0; i < idxs.length; ++i)
          beta[idxs[i]] = this.beta[i];
//        beta[beta.length-1] = this.beta[this.beta.length-1];
      } else
        System.arraycopy(this.beta,0,beta,0,beta.length);
      return beta;
    }


    public double [] getZValues(double [] zValues) {
      Arrays.fill(zValues, Double.NaN); // non-active z-values should not be 0 but Double.NaN
      if(idxs != null){
        for(int i = 0; i < idxs.length; ++i)
          zValues[idxs[i]] = this.zValues[i];
      } else
        System.arraycopy(this.zValues, 0, zValues, 0, zValues.length);
      return zValues;
    }

    public int rank(){
      return idxs != null?idxs.length:(ArrayUtils.countNonzeros(beta));
    }

    public double[] zValues() {
      return zValues.clone();
    }

    public double[] pValues(long residualDegreesOfFreedom) {
      return calculatePValuesFromZValues(zValues, dispersionEstimated, residualDegreesOfFreedom);
    }

    public double[] pValues(double[] zValues, long residualDegreesOfFreedom) {
      return calculatePValuesFromZValues(zValues, dispersionEstimated, residualDegreesOfFreedom);
    }

    public double[] stdErr() {
      return calculateStdErrFromZValues(zValues, beta);
    }

    public double[] stdErr(double[] zValues, double[] beta) {
      return calculateStdErrFromZValues(zValues, beta);
    }

    public Submodel(double lambda, double alpha, double[] beta, int iteration, double devTrain, double devValid,
                    int totBetaLen, double[] zValues, boolean dispersionEstimated) {
      this.lambda_value = lambda;
      this.alpha_value = alpha;
      this.iteration = iteration;
      this.devianceTrain = devTrain;
      this.devianceValid = devValid;
      this.zValues = zValues == null ? null : zValues.clone();
      this.dispersionEstimated = dispersionEstimated;
      int r = 0;
      if(beta != null){
        
          // grab the indices of non-zero coefficients
          for (int i = 0; i < beta.length; ++i) if (beta[i] != 0) ++r;
          if (r < beta.length && beta.length == totBetaLen) { // not been shorten before for multinomial
            idxs = MemoryManager.malloc4(r);
            int j = 0;
            for (int i = 0; i < beta.length; ++i)
              if (beta[i] != 0) idxs[j++] = i;
            this.beta = ArrayUtils.select(beta, idxs);
            if(zValues != null && zValues.length > this.beta.length) { // zValues not shorten yet
              this.zValues = ArrayUtils.select(zValues, idxs); // zValues must correspond to beta
            }
          } else {
            this.beta = beta.clone();
            idxs = null;
          }
          
      } else {
        this.beta = null;
        idxs = null;
      }
    }
  }

  public final double    _lambda_max;
  public final double [] _ymu;
  public final long    _nullDOF;
  public final double    _ySigma;
  public final long      _nobs;
  public double[] _betaCndCheckpoint;  // store temporary beta coefficients for checkpointing purposes
  public boolean _finalScoring = false; // used while scoring to indicate if it is a final or partial scoring 

  private static String[] binomialClassNames = new String[]{"0", "1"};

  @Override
  protected String[][] scoringDomains(){
    String [][] domains = _output._domains;
    if ((_parms._family == Family.binomial || _parms._family == Family.quasibinomial ||
            _parms._family == Family.fractionalbinomial)
            && _output._domains[_output._dinfo.responseChunkId(0)] == null) {
      domains = domains.clone();
      domains[_output._dinfo.responseChunkId(0)] = binomialClassNames;
    }
    return domains;
  }

  public void setZValues(double [] zValues, double dispersion, boolean dispersionEstimated) {
    _output._zvalues = zValues;
    setDispersion(dispersion, dispersionEstimated);
  }
  
  public void setDispersion(double dispersion, boolean dispersionEstimated) {
    _output._dispersion = dispersion;
    _output._dispersionEstimated = dispersionEstimated; 
  }
  
  public static class GLMOutput extends Model.Output {
    Submodel[] _submodels = new Submodel[0];
    DataInfo _dinfo;
    double[] _ymu;
    public String[] _coefficient_names;
    String[] _random_coefficient_names; // for HGLM
    String[] _random_column_names;
    public long _training_time_ms;
    public TwoDimTable _variable_importances;
    public VarImp _varimp;  // should contain the same content as standardized coefficients
    int _lambda_array_size; // store number of lambdas to iterate over
    public double _lambda_1se = -1; // lambda_best+sd(lambda) submodel index; applicable if running lambda search with cv
    public double _lambda_min = -1; // starting lambda value when lambda search is enabled
    public double _lambda_max = -1; // minimum lambda value calculated when lambda search is enabled
    public int _selected_lambda_idx; // lambda index with best deviance
    public int _selected_alpha_idx;     // alpha index with best deviance
    public int _selected_submodel_idx;  // submodel index with best deviance
    public int _best_submodel_idx;      // submodel index with best deviance
    public int _best_lambda_idx;        // the same as best_submodel_idx, kept to ensure backward compatibility
    public Key<Frame> _regression_influence_diagnostics;
    public Key<Frame> _betadiff_var;  // for debugging only, no need to serialize
    public double lambda_best(){return _submodels.length == 0 ? -1 : _submodels[_best_submodel_idx].lambda_value;}
    public double dispersion(){ return _dispersion;}
    public boolean dispersionEstimated() {return _dispersionEstimated;}
    public double alpha_best() { return _submodels.length == 0 ? -1 : _submodels[_selected_submodel_idx].alpha_value;}
    public double lambda_1se(){
      return _lambda_1se; // (_lambda_1se==-1 || _submodels.length==0 || _lambda_1se>=_submodels.length) ? -1 : _submodels[_lambda_1se].lambda_value;
    }
    
    public DataInfo getDinfo() { return _dinfo; }
    public int bestSubmodelIndex() { return _selected_submodel_idx; }
    public double lambda_selected(){
      return _submodels[_selected_submodel_idx].lambda_value;
    }
    final int _totalBetaLength;
    double[] _global_beta;
    double[] _ubeta;  // HGLM:  random coefficients
    private double[] _zvalues;
    double[] _variable_inflation_factors;
    String[] _vif_predictor_names; // predictor names corresponding to the variableInflationFactors
    double [][] _vcov;
    private double _dispersion;
    private boolean _dispersionEstimated;
    public int[] _activeColsPerClass;
    public boolean hasPValues(){return _zvalues != null;}
    public boolean hasVIF() { return _vif_predictor_names != null; }

    public double[] stdErr() {
      return calculateStdErrFromZValues(_zvalues, _global_beta);
    }

    public static double[] calculateStdErrFromZValues(double[] zValues, double[] beta) {
      double[] res = zValues.clone();
      for (int i = 0; i < res.length; ++i) {
        if(beta[i] == 0) {
          res[i] = Double.NaN;
        } else {
          res[i] = beta[i] / zValues[i];
        }
      }
      return res;
    }
    
    public double[] getZValues() {
      return _zvalues;
    }
    public double[] getVariableInflationFactors() { return _variable_inflation_factors; }
    public String[] getVIFPredictorNames() { return _vif_predictor_names; }
    public Map<String, Double> getVIFAndNames() { 
      if (_variable_inflation_factors != null)
        return IntStream.range(0, _vif_predictor_names.length).boxed().collect(toMap(s ->_vif_predictor_names[s], s -> _variable_inflation_factors[s]));
      else
        return null;
    }

    @Override
    public TwoDimTable getVariableImportances() {
      return _variable_importances;
    }

    @Override public ModelCategory getModelCategory() {
      return _binomial?ModelCategory.Binomial:(_multinomial?ModelCategory.Multinomial:(_ordinal?ModelCategory.Ordinal:ModelCategory.Regression));
    }
    @Override
    protected long checksum_impl() {
      long d = _global_beta == null?1:Arrays.hashCode(_global_beta);
      return d*super.checksum_impl();
    }
    public double [] zValues(){return _zvalues.clone();}

    public static double[] calculatePValuesFromZValues(double[] zValues,
                                                       boolean dispersionEstimated,
                                                       long residualDegreesOfFreedom) {
      double[] res = zValues.clone();
      RealDistribution rd = dispersionEstimated ? new TDistribution(residualDegreesOfFreedom) : new NormalDistribution();
      for(int i = 0; i < res.length; ++i) {
        if(!Double.isNaN(zValues[i])) { // if zValues[i] is Nan, then res[i] is already set to NaN (desired value)
          res[i] = 2 * rd.cumulativeProbability(-Math.abs(res[i]));
        }
      }
      return res;
    }

    public double[] pValues() {
      return calculatePValuesFromZValues(_zvalues, _dispersionEstimated, _training_metrics.residual_degrees_of_freedom());
    }

    public double[] variableInflationFactors() {
      return _variable_inflation_factors; // predictor orders the same as in coefficientNames
    }
    double[][] _global_beta_multinomial;
    final int _nclasses;
    public boolean _binomial;
    public boolean _multinomial;
    public boolean _ordinal;

    public void setLambdas(GLMParameters parms) {
      if (parms._lambda_search) {
        _lambda_max = parms._lambda[0];
        _lambda_min = parms._lambda[parms._lambda.length-1];
      }
    }
    
    public int rank() { return _submodels[_selected_submodel_idx].rank();}
    
    public double[] ymu() { return _ymu;}

    public boolean isStandardized() {
      return _dinfo._predictor_transform == TransformType.STANDARDIZE;
    }
    
    public String[] coefficientNames() {
      return _coefficient_names;
    }
    
    

    // This method is to take the coefficient names of one class and extend it to
    // coefficient names for all N classes.
    public String[] multiClassCoeffNames() {
      String[] responseDomain = _domains[_domains.length-1];
      String[] multinomialNames = new String[_coefficient_names.length*responseDomain.length];
      int coeffLen = _coefficient_names.length;
      int responseLen = responseDomain.length;
      int counter = 0;
      for (int respInd = 0; respInd < responseLen; respInd++) {
        for (int coeffInd = 0; coeffInd < coeffLen; coeffInd++) {
          multinomialNames[counter++] = _coefficient_names[coeffInd] + "_" + responseDomain[respInd];
        }
      }
      return multinomialNames;
    }
    
    public String[] randomcoefficientNames() { return _random_coefficient_names; }
    public double[] ubeta() { return _ubeta; }

    // GLM is always supervised
    public boolean isSupervised() { return true; }

    @Override public InteractionBuilder interactionBuilder() { return _dinfo._interactionSpec != null ? new GLMInteractionBuilder() : null; }
    private class GLMInteractionBuilder implements InteractionBuilder {
      @Override
      public Frame makeInteractions(Frame f) {
        InteractionPair[] interactionPairs = _dinfo._interactionSpec.makeInteractionPairs(f);
        f.add(Model.makeInteractions(f, false, interactionPairs, true, true, false));
        return f;
      }
    }
    public static Frame expand(Frame fr, InteractionSpec interactions, boolean useAll, boolean standardize, boolean skipMissing) {
      return MakeGLMModelHandler.oneHot(fr,interactions,useAll,standardize,false,skipMissing);
    }

    public GLMOutput(DataInfo dinfo, String[] column_names, String[] column_types, String[][] domains, 
                     String[] coefficient_names, double[] beta, boolean binomial, boolean multinomial, boolean ordinal) {
      super(dinfo._weights, dinfo._offset, dinfo._fold);
      _dinfo = dinfo.clone();
      setNames(column_names, column_types);
      _domains = domains;
      _coefficient_names = coefficient_names;
      _binomial = binomial;
      _multinomial = multinomial;
      _ordinal = ordinal;
      _nclasses = _binomial?2:(_multinomial || _ordinal?beta.length/coefficient_names.length:1);
      _totalBetaLength = beta.length;
      if(_binomial && domains[domains.length-1] != null) {
        assert domains[domains.length - 1].length == 2:"Unexpected domains " + Arrays.toString(domains);
        binomialClassNames = domains[domains.length - 1];
      }
      assert !ArrayUtils.hasNaNsOrInfs(beta): "Coefficients contain NA or Infs.";
      if (_ordinal || _multinomial)
        _global_beta_multinomial=ArrayUtils.convertTo2DMatrix(beta, coefficient_names.length);
      else
        _global_beta=beta;
      _submodels = new Submodel[]{new Submodel(0, 0, beta, -1, Double.NaN, Double.NaN,
              _totalBetaLength, null, false)};
    }
    
    public GLMOutput() {
      _isSupervised = true; 
      _nclasses = -1;
      _totalBetaLength = -1;
    }

    public GLMOutput(GLM glm) {
      super(glm);
      _dinfo = glm._dinfo.clone();
      _dinfo._adaptedFrame = null;
      String[] cnames = glm._dinfo.coefNames();
      String [] names = glm._dinfo._adaptedFrame._names;
      String [][] domains = glm._dinfo._adaptedFrame.domains();
      if(glm._parms._family == Family.quasibinomial){
        double [] mins = glm._dinfo._adaptedFrame.lastVec().mins();
        double [] maxs = glm._dinfo._adaptedFrame.lastVec().maxs();
        double l = mins[0];
        double u = maxs[0];
        if(!(l<u))
          throw new IllegalArgumentException("quasibinomial family expects response to have two distinct values");
        for(int i = 0; i < mins.length; ++i){
          if((mins[i]-l)*(mins[i]-u) != 0)
            throw new IllegalArgumentException("quasibinomial family expects response to have two distinct values, got mins = " + Arrays.toString(mins) + ", maxs = " + Arrays.toString(maxs));
          if((maxs[i]-l)*(maxs[i]-u) != 0)
            throw new IllegalArgumentException("quasibinomial family expects response to have two distinct values, got mins = " + Arrays.toString(mins) + ", maxs = " + Arrays.toString(maxs));
        }
        domains[domains.length-1] = new String[]{Double.toString(l),Double.toString(u)};
      }
      int id = glm._generatedWeights == null?-1:ArrayUtils.find(names, glm._generatedWeights);
      if(id >= 0) {
        _dinfo._weights = false;
        String [] ns = new String[names.length-1];
        String[][] ds = new String[domains.length-1][];
        System.arraycopy(names,0,ns,0,id);
        System.arraycopy(domains,0,ds,0,id);
        System.arraycopy(names,id+1,ns,id,ns.length-id);
        System.arraycopy(domains,id+1,ds,id,ds.length-id);
        names = ns;
        domains = ds;
      }
      setNames(names, glm._dinfo._adaptedFrame.typesStr());
      _domains = domains;
      _coefficient_names = Arrays.copyOf(cnames, cnames.length + 1);
      if (glm._parms._HGLM) {
        _random_coefficient_names = Arrays.copyOf(glm._randCoeffNames, glm._randCoeffNames.length);
        _random_column_names = Arrays.copyOf(glm._randomColNames, glm._randomColNames.length);
      }
      _coefficient_names[_coefficient_names.length-1] = "Intercept";
      _nclasses = glm.nclasses();
      _totalBetaLength = glm._betaInfo.totalBetaLength();
      _binomial = (glm._parms._family == Family.binomial || glm._parms._family == Family.quasibinomial ||
              Family.fractionalbinomial == glm._parms._family);
      _multinomial = glm._parms._family == Family.multinomial;
      _ordinal = glm._parms._family == Family.ordinal;
    }

    /**
     * Variance Covariance matrix accessor. Available only if odel has been built with p-values.
     * @return
     */
    public double [][] vcov(){return _vcov;}

    @Override
    public int nclasses() {
      return _nclasses;
    }

    @Override
    public String[] classNames() {
      String [] res = super.classNames();
      if(res == null && _binomial)
        return binomialClassNames;
      return res;
    }

    public Submodel pickBestModel(GLMParameters parms) {
      int bestId = 0;
      Submodel best = _submodels[0];
      for(int i = 1; i < _submodels.length; ++i) {
        Submodel sm = _submodels[i];
        if(!(sm.devianceValid > best.devianceValid) && sm.devianceTrain < best.devianceTrain){
          bestId = i;
          best = sm;
        }
      }
      setSubmodelIdx(_best_submodel_idx = bestId, parms);
      return best;
    }

    public double[] getNormBeta() {
      if(this.isStandardized()) {
        return _submodels[_selected_submodel_idx].getBeta(MemoryManager.malloc8d(_dinfo.fullN()+1));
      } else {
        return _dinfo.normalizeBeta(_submodels[_selected_submodel_idx].getBeta(MemoryManager.malloc8d(_dinfo.fullN()+1)), this.isStandardized());

      }
    }

    public double[][] getNormBetaMultinomial() {
      return getNormBetaMultinomial(_selected_submodel_idx, this.isStandardized());
    }

    public double[][] getNormBetaMultinomial(int idx) {
      if(_submodels == null || _submodels.length == 0) // no model yet
        return null;
      double [][] res = new double[nclasses()][];
      Submodel sm = _submodels[idx];
      int N = _dinfo.fullN()+1;
      double [] beta = sm.beta;
      if(sm.idxs != null) {
        beta = ArrayUtils.expandAndScatter(beta, nclasses() * (_dinfo.fullN() + 1), sm.idxs);
      } else if (beta.length < _totalBetaLength && sm.idxs == null) { // need to expand beta to full length
        beta = expandToFullArray(beta, _activeColsPerClass, _totalBetaLength, nclasses(),
                _totalBetaLength/nclasses());
      }
        
      for(int i = 0; i < res.length; ++i)
        res[i] = Arrays.copyOfRange(beta,i*N,(i+1)*N);
      return res;
    }

    public double[][] getNormBetaMultinomial(int idx, boolean standardized) {
      if(_submodels == null || _submodels.length == 0) // no model yet
        return null;
      double [][] res = new double[nclasses()][];
      Submodel sm = _submodels[idx];
      int N = _dinfo.fullN()+1;
      double [] beta = sm.beta;
      if(sm.idxs != null)
        beta = ArrayUtils.expandAndScatter(beta,nclasses()*(_dinfo.fullN()+1),sm.idxs);
      else if (beta.length < _totalBetaLength) // sm.idxs is null but beta too short
        beta = expandToFullArray(beta, _activeColsPerClass, _totalBetaLength, nclasses(),
                _totalBetaLength/nclasses());
      for(int i = 0; i < res.length; ++i)
        if(standardized) {
          res[i] = Arrays.copyOfRange(beta, i * N, (i + 1) * N);
        } else {
           res[i] = _dinfo.normalizeBeta(Arrays.copyOfRange(beta, i * N, (i + 1) * N), standardized);
        }
      return res;
    }

    public double[][] get_global_beta_multinomial(){return _global_beta_multinomial;}

    private int indexOf(double needle, double[] haystack) {
      for (int i = 0; i < haystack.length; i++) {
        if (needle == haystack[i]) return i;
      }
      return -1;
    }

    // set model coefficients to that of submodel index l
    public void setSubmodelIdx(int l, GLMParameters parms){
      _selected_submodel_idx = l;
      _best_lambda_idx = l; // kept to ensure backward compatibility
      _selected_alpha_idx = indexOf(_submodels[l].alpha_value, parms._alpha);
      _selected_lambda_idx = indexOf(_submodels[l].lambda_value, parms._lambda);

      if (_random_coefficient_names != null) 
        _ubeta = Arrays.copyOf(_submodels[l].ubeta, _submodels[l].ubeta.length);
      if(_multinomial || _ordinal) {
        _global_beta_multinomial = getNormBetaMultinomial(l);
        for(int i = 0; i < _global_beta_multinomial.length; ++i)
          _global_beta_multinomial[i] = _dinfo.denormalizeBeta(_global_beta_multinomial[i]);
      } else {
        if (_global_beta == null)
          _global_beta = MemoryManager.malloc8d(_coefficient_names.length);
        else
          Arrays.fill(_global_beta, 0);
        _submodels[l].getBeta(_global_beta);
        _global_beta = _dinfo.denormalizeBeta(_global_beta);
      }
    }
    public double [] beta() { return _global_beta;}
    public Submodel bestSubmodel() {
      return _submodels[_selected_submodel_idx];
    }

    // given lambda value, return the corresponding submodel index
    public Submodel getSubmodel(double lambdaCVEstimate) {
      for(int i = 0; i < _submodels.length; ++i)
        if(_submodels[i] != null && _submodels[i].lambda_value == lambdaCVEstimate) {
          return _submodels[i];
        }
      return null;
    }

    public Submodel getSubmodel(int submodel_index) {
      assert submodel_index < _submodels.length : "submodel_index specified exceeds the submodels length.";
      return _submodels[submodel_index];
    }
    
    // calculate variable importance which is derived from the standardized coefficients
    public VarImp calculateVarimp() {
      String[] names = coefficientNames();
      final double [] magnitudes = new double[names.length];
      int len = magnitudes.length - 1;
      if (len == 0) // GLM model contains only intercepts and no predictor coefficients.
        return null;
      
      int[] indices = new int[len];
      for (int i = 0; i < indices.length; ++i)
        indices[i] = i;
      float[] magnitudesSort = new float[len];  // stored sorted coefficient magnitudes
      String[] namesSort = new String[len];
      
      if (_nclasses > 2)
        calculateVarimpMultinomial(magnitudes, indices, getNormBetaMultinomial());
      else
        calculateVarimpBase(magnitudes, indices, getNormBeta());
      
      for (int index = 0; index < len; index++) {
        magnitudesSort[index] = (float) magnitudes[indices[index]];
        namesSort[index] = names[indices[index]];
      }
      return new VarImp(magnitudesSort, namesSort);
    }
  }


  /**
   * get beta coefficients in a map indexed by name
   * @return the estimated coefficients
   */
  public HashMap<String,Double> coefficients(){
    HashMap<String, Double> res = new HashMap<>();
    final double [] b = beta();
    if(b == null) return res;
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal){
      String [] responseDomain = _output._domains[_output._domains.length-1];
      int len = b.length/_output.nclasses();
      assert b.length == len*_output.nclasses();
      for(int c = 0; c < _output.nclasses(); ++c) {
        String postfix =  "_"+responseDomain[c];
        for (int i = 0; i < len; ++i)
          res.put(_output._coefficient_names[i]+postfix, b[c*len+i]);
      }
    } else for (int i = 0; i < b.length; ++i)
        res.put(_output._coefficient_names[i], b[i]);
    return res;
  }

  public HashMap<String,Double> coefficients(boolean standardized){
    HashMap<String, Double> res = new HashMap<>();
    double [] b = beta();
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal){
      if (standardized) b = flat(this._output.getNormBetaMultinomial());
      if(b == null) return res;
      String [] responseDomain = _output._domains[_output._domains.length-1];
      int len = b.length/_output.nclasses();
      assert b.length == len*_output.nclasses();
      for(int c = 0; c < _output.nclasses(); ++c) {
        String postfix =  "_" + responseDomain[c];
        for (int i = 0; i < len; ++i)
          res.put(_output._coefficient_names[i]+postfix, b[c*len+i]);
      }
    } else {
      if (standardized) b = this._output.getNormBeta();
      for (int i = 0; i < b.length; ++i)
        res.put(_output._coefficient_names[i], b[i]);
    }
    return res;
  }
  
  // TODO: Shouldn't this be in schema? have it here for now to be consistent with others...
  /**
   * Re-do the TwoDim table generation with updated model.
   */
  public TwoDimTable generateSummary(Key train, int iter){
      String[] names = new String[]{"Family", "Link", "Regularization", "Number of Predictors Total", "Number of Active Predictors", "Number of Iterations", "Training Frame"};
      String[] types = new String[]{"string", "string", "string", "int", "int", "int", "string"};
      String[] formats = new String[]{"%s", "%s", "%s", "%d", "%d", "%d", "%s"};
      if (_parms._lambda_search) {
        names = new String[]{"Family", "Link", "Regularization", "Lambda Search", "Number of Predictors Total", "Number of Active Predictors", "Number of Iterations", "Training Frame"};
        types = new String[]{"string", "string", "string", "string", "int", "int", "int", "string"};
        formats = new String[]{"%s", "%s", "%s", "%s", "%d", "%d", "%d", "%s"};
      }
      _output._model_summary = new TwoDimTable("GLM Model", "summary", new String[]{""}, names, types, formats, "");
      _output._model_summary.set(0, 0, _parms._family.toString());
      _output._model_summary.set(0, 1, _parms._link.toString());
      String regularization = "None";
      if (_parms._lambda != null && !(_parms._lambda.length == 1 && _parms._lambda[0] == 0)) { // have regularization
        if (_output.bestSubmodel().alpha_value == 0)
          regularization = "Ridge ( lambda = ";
        else if (_output.bestSubmodel().alpha_value == 1)
          regularization = "Lasso (lambda = ";
        else
          regularization = "Elastic Net (alpha = " + MathUtils.roundToNDigits(_output.bestSubmodel().alpha_value, 4) + ", lambda = ";
        regularization = regularization + MathUtils.roundToNDigits(_output.bestSubmodel().lambda_value, 4) + " )";
      }
      _output._model_summary.set(0, 2, regularization);
      int lambdaSearch = 0;
      if (_parms._lambda_search) {
        lambdaSearch = 1;
        iter = _output._submodels[_output._selected_submodel_idx].iteration;
        _output._model_summary.set(0, 3, "nlambda = " + _parms._nlambdas + ", lambda.max = " + MathUtils.roundToNDigits(_lambda_max, 4) + ", lambda.min = " + MathUtils.roundToNDigits(_output.lambda_best(), 4) + ", lambda.1se = " + MathUtils.roundToNDigits(_output.lambda_1se(), 4));
      }
      int intercept = _parms._intercept ? 1 : 0;
      if (_output.nclasses() > 2) {
        _output._model_summary.set(0, 3 + lambdaSearch, _output.nclasses() * _output._coefficient_names.length);
        _output._model_summary.set(0, 4 + lambdaSearch, Integer.toString(_output.rank() - _output.nclasses() * intercept));
      } else {
        _output._model_summary.set(0, 3 + lambdaSearch, beta().length - 1);
        _output._model_summary.set(0, 4 + lambdaSearch, Integer.toString(_output.rank() - intercept));
      }
      _output._model_summary.set(0, 5 + lambdaSearch, Integer.valueOf(iter));
      _output._model_summary.set(0, 6 + lambdaSearch, train.toString());
      return _output._model_summary;
  }

  /***
   * This one is for HGLM
   * @param train
   * @param iter
   * @return
   */
  public TwoDimTable generateSummaryHGLM(Key train, int iter){
    String[] names = new String[]{"Family", "Link", "Number of Predictors Total", "Number of Active Predictors", "Number of Iterations", "Training Frame"};
    String[] types = new String[]{"string", "string", "int", "int", "int", "string"};
    String[] formats = new String[]{"%s", "%s", "%d", "%d", "%d", "%s"};
    int numRand = _parms._rand_family.length;
    String[] rand_family_links = new String[numRand * 2];
    for (int index = 0; index < numRand; index++) {
      int tindex = index * 2;
      rand_family_links[tindex] = "rand_family_for_column_" + index;
      rand_family_links[tindex + 1] = "rand_link_for_column_" + index;
    }
    int totListLen = _parms._rand_family.length * 2 + names.length;
    String[] tnames = new String[totListLen];
    String[] ttypes = new String[totListLen];
    String[] tformats = new String[totListLen];
    System.arraycopy(names, 0, tnames, 0, 2); // copy family, link
    System.arraycopy(types, 0, ttypes, 0, 2);
    System.arraycopy(formats, 0, tformats, 0, 2);
    int numCopy = 2 * numRand;
    for (int index = 0; index < numCopy; index++) { // insert random family/link info
      tnames[index + 2] = rand_family_links[index];
      ttypes[index + 2] = "string";
      tformats[index + 2] = "%s";
    }
    int offset = 2 + numCopy;
    int copyLength = names.length - 2;
    System.arraycopy(names, 2, tnames, offset, copyLength); // copy remaining of original names
    System.arraycopy(types, 2, ttypes, offset, copyLength);
    System.arraycopy(formats, 2, tformats, offset, copyLength);
    _output._model_summary = new TwoDimTable("HGLM Model", "summary", new String[]{""},
            tnames, ttypes, tformats, "");
    int tableColIndex = 0;
    _output._model_summary.set(0, tableColIndex++, _parms._family.toString());
    _output._model_summary.set(0, tableColIndex++, _parms._link.toString());
    int numFamily = _parms._rand_family.length;
    for (int index = 0; index < numFamily; index++) {
      _output._model_summary.set(0, tableColIndex++, _parms._rand_family[index].name());
      if (_parms._rand_link == null)
        _output._model_summary.set(0, tableColIndex++, _parms._rand_family[index].defaultLink.name());
      else
        _output._model_summary.set(0, tableColIndex++, _parms._rand_link[index].name());
    }
    int intercept = _parms._intercept ? 1 : 0;
    _output._model_summary.set(0, tableColIndex++, beta().length - 1);
    _output._model_summary.set(0, tableColIndex++, Integer.toString(_output.rank() - intercept));
    _output._model_summary.set(0, tableColIndex++, Integer.valueOf(iter));
    _output._model_summary.set(0, tableColIndex, train.toString());
    return _output._model_summary;
  }


  @Override public long checksum_impl(){
    if(_parms._train == null) return 0;
    return super.checksum_impl();
  }

  private static ThreadLocal<double[]> _eta = new ThreadLocal<>();

  @Override protected double[] score0(double[] data, double[] preds){return score0(data,preds,0);}
  @Override protected double[] score0(double[] data, double[] preds, double o) {
    if(_parms._family == Family.multinomial || _parms._family == Family.ordinal) {
      if (o != 0) throw H2O.unimpl("Offset is not implemented for multinomial/ordinal.");
      double[] eta = _eta.get();
      Arrays.fill(preds, 0.0);
      if (eta == null || eta.length != _output.nclasses()) _eta.set(eta = MemoryManager.malloc8d(_output.nclasses()));
      final double[][] bm = _output._global_beta_multinomial;
      double sumExp = 0;
      double maxRow = 0;
      int classInd = bm.length;
      int icptInd = bm[0].length-1;
      if (_parms._family == Family.ordinal) // only need one eta for all classes
        classInd -= 1;  // last class all zeros
      for (int c = 0; c < classInd; ++c) {
        double e = bm[c][icptInd]; // grab the intercept, replace the bm[0].length-1
        double [] b = bm[c];
        for(int i = 0; i < _output._dinfo._cats; ++i) {
          int l = _output._dinfo.getCategoricalId(i, data[i]);
          if (l >= 0) e += b[l];
        }
        int coff = _output._dinfo._cats;
        int boff = _output._dinfo.numStart();
        for(int i = 0; i < _output._dinfo._nums; ++i) {
          double d = data[coff+i];
          if(!_output._dinfo._skipMissing && Double.isNaN(d))
            d = _output._dinfo._numNAFill[i];
          e += d*b[boff+i];
        }
        if(e > maxRow) maxRow = e;
        eta[c] = e;
      }
      if (_parms._family == Family.multinomial) {
        for (int c = 0; c < bm.length; ++c)
          sumExp += eta[c] = Math.exp(eta[c]-maxRow); // intercept
        sumExp = 1.0 / sumExp;
        for (int c = 0; c < bm.length; ++c)
          preds[c + 1] = eta[c] * sumExp;
        preds[0] = ArrayUtils.maxIndex(eta);
      } else {  // scoring for ordinal
        int nclasses = _output._nclasses;
        int lastClass = nclasses-1;
        // first assign the class
        Arrays.fill(preds,1e-10); // initialize to small number
        preds[0] = lastClass;  // initialize to last class by default here
        double previousCDF = 0.0;
        for (int cInd = 0; cInd < lastClass; cInd++) { // classify row and calculate PDF of each class
          double currEta = eta[cInd];
          double currCDF = 1.0 / (1 + Math.exp(-currEta));
          preds[cInd + 1] = currCDF - previousCDF;
          previousCDF = currCDF;

          if (currEta > 0) { // found the correct class
            preds[0] = cInd;
            break;
          }
        }
        for (int cInd = (int) preds[0] + 1; cInd < lastClass; cInd++) {  // continue PDF calculation
          double currCDF = 1.0 / (1 + Math.exp(-eta[cInd]));
          preds[cInd + 1] = currCDF - previousCDF;
          previousCDF = currCDF;
        }
        preds[nclasses] = 1-previousCDF;
      }
    } else {
      double[] b = beta();
      double eta = b[b.length - 1] + o; // intercept + offset
      for (int i = 0; i < _output._dinfo._cats && !Double.isNaN(eta); ++i) {
        int l = _output._dinfo.getCategoricalId(i, data[i]);
        if (l >= 0) eta += b[l];
      }
      int numStart = _output._dinfo.numStart();
      int ncats = _output._dinfo._cats;
      for (int i = 0; i < _output._dinfo._nums && !Double.isNaN(eta); ++i) {
        double d = data[ncats + i];
        if (!_output._dinfo._skipMissing && Double.isNaN(d))
          d = _output._dinfo._numNAFill[i];
        eta += b[numStart + i] * d;
      }
      double mu = _parms.linkInv(eta);
      if (_parms._family == Family.binomial) { // threshold for prediction
        preds[0] = mu >= defaultThreshold()?1:0;
        preds[1] = 1.0 - mu; // class 0
        preds[2] = mu; // class 1
      } else
        preds[0] = mu;
    }
    return preds;
  }
  @Override protected boolean needsPostProcess() { return false; /* pred[0] is already set by score0 */ }

  @Override
  public double score(double[] data) {
    double[] pred = score0(data, new double[_output.nclasses() + 1], 0);
    return pred[0];
  }
  
  @Override protected void toJavaPredictBody(SBPrintStream body,
                                             CodeGeneratorPipeline classCtx,
                                             CodeGeneratorPipeline fileCtx,
                                             final boolean verboseCode) {
    // Generate static fields
    classCtx.add(new CodeGenerator() {
      @Override
      public void generate(JCodeSB out) {
        JCodeGen.toClassWithArray(out, "public static", "BETA", beta_internal()); // "The Coefficients"
        JCodeGen.toClassWithArray(out, "static", "NUM_MEANS", _output._dinfo._numNAFill,"Imputed numeric values");
        JCodeGen.toClassWithArray(out, "static", "CAT_MODES", _output._dinfo.catNAFill(),"Imputed categorical values.");
        JCodeGen.toStaticVar(out, "CATOFFS", dinfo()._catOffsets, "Categorical Offsets");
      }
    });
    body.ip("final double [] b = BETA.VALUES;").nl();
    if(_parms.imputeMissing()){
      body.ip("for(int i = 0; i < " + _output._dinfo._cats + "; ++i) if(Double.isNaN(data[i])) data[i] = CAT_MODES.VALUES[i];").nl();
      body.ip("for(int i = 0; i < " + _output._dinfo._nums + "; ++i) if(Double.isNaN(data[i + " + _output._dinfo._cats + "])) data[i+" + _output._dinfo._cats + "] = NUM_MEANS.VALUES[i];").nl();
    }
    if(_parms._family != Family.multinomial && _parms._family != Family.ordinal) {
      body.ip("double eta = 0.0;").nl();
      if (!_parms._use_all_factor_levels) { // skip level 0 of all factors
        body.ip("for(int i = 0; i < CATOFFS.length-1; ++i) if(data[i] != 0) {").nl();
        body.ip("  int ival = (int)data[i] - 1;").nl();
        body.ip("  if(ival != data[i] - 1) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
        body.ip("  ival += CATOFFS[i];").nl();
        body.ip("  if(ival < CATOFFS[i + 1])").nl();
        body.ip("    eta += b[ival];").nl();
      } else { // do not skip any levels
        body.ip("for(int i = 0; i < CATOFFS.length-1; ++i) {").nl();
        body.ip("  int ival = (int)data[i];").nl();
        body.ip("  if(ival != data[i]) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
        body.ip("  ival += CATOFFS[i];").nl();
        body.ip("  if(ival < CATOFFS[i + 1])").nl();
        body.ip("    eta += b[ival];").nl();
      }
      body.ip("}").nl();
      final int noff = dinfo().numStart() - dinfo()._cats;
      body.ip("for(int i = ").p(dinfo()._cats).p("; i < b.length-1-").p(noff).p("; ++i)").nl();
      body.ip("eta += b[").p(noff).p("+i]*data[i];").nl();
      body.ip("eta += b[b.length-1]; // reduce intercept").nl();
      if(_parms._family != Family.tweedie)
        body.ip("double mu = hex.genmodel.GenModel.GLM_").p(_parms._link.toString()).p("Inv(eta");
      else
        body.ip("double mu = hex.genmodel.GenModel.GLM_tweedieInv(eta," + _parms._tweedie_link_power);
      body.p(");").nl();
      if (_parms._family == Family.binomial || _parms._family == Family.fractionalbinomial) {
        body.ip("preds[0] = (mu >= ").p(defaultThreshold()).p(") ? 1 : 0").p("; // threshold given by ROC").nl();
        body.ip("preds[1] = 1.0 - mu; // class 0").nl();
        body.ip("preds[2] =       mu; // class 1").nl();
      } else {
        body.ip("preds[0] = mu;").nl();
      }
    } else {
      int P = _output._global_beta_multinomial[0].length;
      body.ip("preds[0] = 0;").nl();
      body.ip("for(int c = 0; c < " + _output._nclasses + "; ++c){").nl();
      body.ip("  preds[c+1] = 0;").nl();
      if(dinfo()._cats > 0) {
        if (!_parms._use_all_factor_levels) { // skip level 0 of all factors
          body.ip("  for(int i = 0; i < CATOFFS.length-1; ++i) if(data[i] != 0) {").nl();
          body.ip("    int ival = (int)data[i] - 1;").nl();
          body.ip("    if(ival != data[i] - 1) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
          body.ip("    ival += CATOFFS[i];").nl();
          body.ip("    if(ival < CATOFFS[i + 1])").nl();
          body.ip("      preds[c+1] += b[ival+c*" + P + "];").nl();
        } else { // do not skip any levels
          body.ip("  for(int i = 0; i < CATOFFS.length-1; ++i) {").nl();
          body.ip("    int ival = (int)data[i];").nl();
          body.ip("    if(ival != data[i]) throw new IllegalArgumentException(\"categorical value out of range\");").nl();
          body.ip("    ival += CATOFFS[i];").nl();
          body.ip("    if(ival < CATOFFS[i + 1])").nl();
          body.ip("      preds[c+1] += b[ival+c*" + P + "];").nl();
        }
        body.ip("  }").nl();
      }
      final int noff = dinfo().numStart();
      body.ip("  for(int i = 0; i < " + dinfo()._nums + "; ++i)").nl();
      body.ip("    preds[c+1] += b[" + noff + "+i + c*" + P + "]*data[i+"+dinfo()._cats+"];").nl();
      body.ip("  preds[c+1] += b[" + (P-1) +" + c*" + P + "]; // reduce intercept").nl();
      body.ip("}").nl();
      if (_parms._family == Family.multinomial) {
        body.ip("double max_row = 0;").nl();
        body.ip("for(int c = 1; c < preds.length; ++c) if(preds[c] > max_row) max_row = preds[c];").nl();
        body.ip("double sum_exp = 0;").nl();
        body.ip("for(int c = 1; c < preds.length; ++c) { sum_exp += (preds[c] = Math.exp(preds[c]-max_row));}").nl();
        body.ip("sum_exp = 1/sum_exp;").nl();
        body.ip("double max_p = 0;").nl();
        body.ip("for(int c = 1; c < preds.length; ++c) if((preds[c] *= sum_exp) > max_p){ max_p = preds[c]; preds[0] = c-1;};").nl();
      } else {  // special for ordinal.  preds contains etas for all classes
        int lastClass = _output._nclasses-1;
        body.ip("int lastClass = "+lastClass+";").nl();
        body.ip("preds[0]=0;").nl();
        body.ip("double previousCDF = 0.0;").nl();
        body.ip("for (int cInd = 0; cInd < lastClass; cInd++) { // classify row and calculate PDF of each class").nl();
        body.ip("  double eta = preds[cInd+1];").nl();
        body.ip("  double currCDF = 1.0/(1+Math.exp(-eta));").nl();
        body.ip("  preds[cInd+1] = currCDF-previousCDF;").nl();
        body.ip("  previousCDF = currCDF;").nl();
        body.ip("}").nl();
        body.ip("preds[nclasses()] = 1-previousCDF;").nl();
        body.ip("double max_p = 0;").nl();
        body.ip("for(int c = 1; c < preds.length; ++c) if(preds[c] > max_p){ max_p = preds[c]; preds[0] = c-1;};").nl();
      }
    }
  }

  @Override protected SBPrintStream toJavaInit(SBPrintStream sb, CodeGeneratorPipeline fileCtx) {
    sb.nl();
    sb.ip("public boolean isSupervised() { return true; }").nl();
    sb.ip("public int nfeatures() { return "+_output.nfeatures()+"; }").nl();
    sb.ip("public int nclasses() { return "+_output.nclasses()+"; }").nl();
    return sb;
  }

  private GLMScore makeScoringTask(Frame adaptFrm, boolean generatePredictions, Job j, boolean computeMetrics, CFuncRef customMetric){
    int responseId = adaptFrm.find(_output.responseName());
    if(responseId > -1 && adaptFrm.vec(responseId).isBad()) { // remove inserted invalid response
      adaptFrm = new Frame(adaptFrm.names(),adaptFrm.vecs());
      adaptFrm.remove(responseId);
    }
    // Build up the names & domains.
    final boolean detectedComputeMetrics = computeMetrics && (adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad());
    String [] domain = _output.nclasses()<=1 ? null : !detectedComputeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain();
    // Score the dataset, building the class distribution & predictions
    return new GLMScore(j, this, _output._dinfo.scoringInfo(_output._names,adaptFrm),domain,detectedComputeMetrics, generatePredictions, customMetric);
  }
  /** Score an already adapted frame.  Returns a new Frame with new result
   *  vectors, all in the DKV.  Caller responsible for deleting.  Input is
   *  already adapted to the Model's domain, so the output is also.  Also
   *  computes the metrics for this frame.
   *
   * @param adaptFrm Already adapted frame
   * @param computeMetrics
   * @return A Frame containing the prediction column, and class distribution
   */
  @Override
  protected PredictScoreResult predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j, boolean computeMetrics, CFuncRef customMetricFunc) {
    String [] names = makeScoringNames();
    String [][] domains = new String[names.length][];
    GLMScore gs = makeScoringTask(adaptFrm,true,j, computeMetrics, customMetricFunc);
    assert gs._dinfo._valid:"_valid flag should be set on data info when doing scoring";
    gs.doAll(names.length,Vec.T_NUM,gs._dinfo._adaptedFrame);
    ModelMetrics.MetricBuilder<?> mb = null;
    Frame rawFrame = null;
    if (gs._computeMetrics) {
      mb = gs._mb;
      rawFrame = gs.outputFrame();
    }
    domains[0] = gs._domain;
    Frame outputFrame = gs.outputFrame(Key.make(destination_key), names, domains);
    return new PredictScoreResult(mb, rawFrame, outputFrame);
  }

  @Override public String [] makeScoringNames(){
    String [] res = super.makeScoringNames();
    if(_output._vcov != null) res = ArrayUtils.append(res,"StdErr");
    return res;
  }
  /** Score an already adapted frame.  Returns a MetricBuilder that can be used to make a model metrics.
   * @param adaptFrm Already adapted frame
   * @return MetricBuilder
   */
  @Override
  protected ModelMetrics.MetricBuilder scoreMetrics(Frame adaptFrm) {
    GLMScore gs = makeScoringTask(adaptFrm,false,null, true, CFuncRef.from(_parms._custom_metric_func));// doAll(names.length,Vec.T_NUM,adaptFrm);
    assert gs._dinfo._valid:"_valid flag should be set on data info when doing scoring";
    return gs.doAll(gs._dinfo._adaptedFrame)._mb;
  }

  @Override
  public boolean haveMojo() {
    if (!_parms._HGLM && _parms.interactionSpec() == null) return super.haveMojo();
    else return false;
  }

  @Override
  public boolean havePojo() {
    if (!_parms._HGLM && _parms.interactionSpec() == null && _parms._offset_column == null) return super.havePojo();
    else return false;
  }

  @Override
  public GLMMojoWriter getMojo() {
    return new GLMMojoWriter(this);
  }

  private boolean isFeatureUsedInPredict(int featureIdx, double[] beta){
    if (featureIdx < _output._dinfo._catOffsets.length - 1 && _output._column_types[featureIdx].equals("Enum")) {
      for (int i = _output._dinfo._catOffsets[featureIdx];
           i < _output._dinfo._catOffsets[featureIdx + 1];
           i++) {
        if (beta[i] != 0) return true;
      }
      return false;
    } else {
      featureIdx += _output._dinfo._numOffsets[0] - _output._dinfo._catOffsets.length + 1;
    }
    return beta[featureIdx] != 0;
}

  @Override
  protected boolean isFeatureUsedInPredict(int featureIdx) {
    if (_parms._interactions != null) return true;
    if (_output.isMultinomialClassifier()) {
      for (double[] classBeta : _output._global_beta_multinomial) {
        if (isFeatureUsedInPredict(featureIdx, classBeta))
          return true;
      }
      return false;
    } else {
      return isFeatureUsedInPredict(featureIdx, _output._global_beta);
    }
  }

  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    super.remove_impl(fs, cascade);
    Keyed.remove(_output._regression_influence_diagnostics, fs, cascade);
    return fs;
  }

  @Override
  protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    if (_output._regression_influence_diagnostics != null) ab.putKey(_output._regression_influence_diagnostics);
    return super.writeAll_impl(ab);
  }

  @Override
  protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    if (_output._regression_influence_diagnostics!= null)
      ab.getKey(_output._regression_influence_diagnostics, fs);
    return super.readAll_impl(ab, fs);
  }  
}
