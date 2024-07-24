package hex.hglm;

import hex.DataInfo;
import hex.Model;
import hex.ModelCategory;
import hex.ModelMetrics;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.*;
import water.fvec.Frame;
import water.fvec.Vec;
import water.udf.CFuncRef;
import water.util.TwoDimTable;

import java.io.Serializable;
import java.util.Arrays;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.hglm.HGLMModel.HGLMParameters.Method.EM;
import static hex.hglm.HGLMUtils.*;
import static water.util.ArrayUtils.copy2DArray;

public class HGLMModel extends Model<HGLMModel, HGLMModel.HGLMParameters, HGLMModel.HGLMModelOutput> {
  /**
   * the doc = document attached to https://github.com/h2oai/h2o-3/issues/8487, title HGLM_H2O_Implementation.pdf
   * I will be referring to the doc and different parts of it to explain my implementation.
   */
  public HGLMModel(Key<HGLMModel> selfKey, HGLMParameters parms, HGLMModelOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return new MetricBuilderHGLM(domain, true, true, _parms._random_intercept, _output);
  }
  
  @Override
  public String[] makeScoringNames() {
    return new String[]{"predict"};
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    throw new UnsupportedOperationException("HGLMModel.score0 should never be called");
  }

  @Override
  protected PredictScoreResult predictScoreImpl(Frame fr, Frame adaptFrm, String destination_key, Job j,
                                                boolean computeMetrics, CFuncRef customMetricFunc) {
    String[] predictNames = makeScoringNames();
    String[][] domains = new String[predictNames.length][];
    boolean forTraining = _parms.train().getKey().equals(fr.getKey());
    HGLMScore gs = makeScoringTask(adaptFrm, true, j, computeMetrics && !_parms._gen_syn_data);
    gs.doAll(predictNames.length, Vec.T_NUM, gs._dinfo._adaptedFrame);
    MetricBuilderHGLM mb = null;
    Frame rawFrame = null;
    if (gs._computeMetrics) { // only calculate log-likelihood, mse and other metrics if _computeMetrics
      mb = gs._mb;
      if (forTraining) {
        _output._yMinusXTimesZ = gs._yMinusXTimesZ;
        _output._yMinusFixPredSquare = mb._yMinusFixPredSquare;
      } else {  // store for all frames other than the training frame
        _output._yMinusXTimesZValid = gs._yMinusXTimesZ;
        _output._yMinusFixPredSquareValid = mb._yMinusFixPredSquare;
      }
      rawFrame = gs.outputFrame();
    }
    domains[0] = gs._predDomains;
    Frame outputFrame = gs.outputFrame(Key.make(destination_key), predictNames, domains);
    return new PredictScoreResult(mb, rawFrame, outputFrame);
  }

  private HGLMScore makeScoringTask(Frame adaptFrm, boolean makePredictions, Job j, boolean computeMetrics) {
    int responseId = adaptFrm.find(_output.responseName());
    if(responseId > -1 && adaptFrm.vec(responseId).isBad()) { // remove inserted invalid response
      adaptFrm = new Frame(adaptFrm.names(),adaptFrm.vecs());
      adaptFrm.remove(responseId);
    }
    final boolean detectedComputeMetrics = computeMetrics && (adaptFrm.vec(_output.responseName()) != null && !adaptFrm.vec(_output.responseName()).isBad());
    String [] domain = _output.nclasses()<=1 ? null : (!detectedComputeMetrics ? _output._domains[_output._domains.length-1] : adaptFrm.lastVec().domain());
    return new HGLMScore(j, this, _output._dinfo.scoringInfo(_output._names, adaptFrm), domain, computeMetrics, makePredictions);
  }

  public static class HGLMParameters extends Model.Parameters {
    public long _seed = -1;
    public GLMModel.GLMParameters.Family _family;
    public int _max_iterations = -1;
    public double[] _initial_fixed_effects; // initial values of fixed coefficients   
    public Key _initial_random_effects; // frame key that contains the initial starting values of random coefficient effects
    public Key _initial_t_matrix; // frame key taht contains the initial starting values of T matrix    
    public double _tau_u_var_init = 0;  // initial random coefficient effects variance estimate, set by user
    public double _tau_e_var_init = 0;   // initial random noise variance estimate, set by user
    public GLMModel.GLMParameters.Family _random_family = gaussian;
    public String[] _random_columns;  // store predictors that have random components in the coefficients
    public Method _method;
    public double _em_epsilon = 1e-3;
    public boolean _random_intercept = true;
    public String _group_column;
    public Serializable _missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
    public Key<Frame> _plug_values = null;
    public boolean _use_all_factor_levels = false;
    public boolean _showFixedMatVecs = false; // internal parameter, if true, will show AfjTY, ArjTY, ArjTArj, AfjTAfj, AfjTArj
    public int _score_iteration_interval = 5;
    public boolean _score_each_iteration = false;
    public boolean _gen_syn_data = false;
    
    @Override
    public String algoName() {
      return "HGLM";
    }

    @Override
    public String fullName() {
      return "Hierarchical Generalized Linear Model";
    }

    @Override
    public String javaName() {
      return HGLMModel.class.getName();
    }

    @Override
    public long progressUnits() {
      return 1;
    }

    public enum Method {EM}; // EM: expectation maximization
    
    public HGLMParameters() {
      super();
      _family = gaussian;
      _method = EM;
    }

    public GLMModel.GLMParameters.MissingValuesHandling missingValuesHandling() {
      if (_missing_values_handling instanceof GLMModel.GLMParameters.MissingValuesHandling)
        return (GLMModel.GLMParameters.MissingValuesHandling) _missing_values_handling;
      assert _missing_values_handling instanceof DeepLearningModel.DeepLearningParameters.MissingValuesHandling;
      switch ((DeepLearningModel.DeepLearningParameters.MissingValuesHandling) _missing_values_handling) {
        case MeanImputation:
          return GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
        case Skip:
          return GLMModel.GLMParameters.MissingValuesHandling.Skip;
        default:
          throw new IllegalStateException("Unsupported missing values handling value: " + _missing_values_handling);
      }
    }

    public boolean imputeMissing() {
      return missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.MeanImputation ||
              missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.PlugValues;
    }

    public DataInfo.Imputer makeImputer() {
      if (missingValuesHandling() == GLMModel.GLMParameters.MissingValuesHandling.PlugValues) {
        if (_plug_values == null || _plug_values.get() == null) {
          throw new IllegalStateException("Plug values frame needs to be specified when Missing Value Handling = PlugValues.");
        }
        return new GLM.PlugValuesImputer(_plug_values.get());
      } else { // mean/mode imputation and skip (even skip needs an imputer right now! PUBDEV-6809)
        return new DataInfo.MeanImputer();
      }
    }
  }
  
  public static class HGLMModelOutput extends Model.Output {
    public DataInfo _dinfo;
    final GLMModel.GLMParameters.Family _family;
    final GLMModel.GLMParameters.Family _random_family;
    public String[] _fixed_coefficient_names; // include intercept only if _parms._intercept is true
    public String[] _random_coefficient_names;  // include intercept only if _parms._random_intercept = true
    public String[] _group_column_names;
    public long _training_time_ms;
    public double[] _beta;   // fixed coefficients
    public double[][] _ubeta;  // random coefficients
    public double[][] _tmat;  // calculated with non-standardize random effects coefficients
    double _tauUVar;
    public double _tau_e_var;
    // test parameters
    public double[][] _afjtyj;
    public double[][] _arjtyj;
    public double[][][] _afjtafj;
    public double[][][] _arjtarj;
    public double[][][] _afjtarj;
    public double[][] _yMinusXTimesZ; // generate during training
    public double[][] _yMinusXTimesZValid; // store same value for frames other than training frame
    public int _num_fixed_coeffs;
    public int _num_random_coeffs;
    int[] _randomCatIndices;
    int[] _randomNumIndices;
    int[] _randomCatArrayStartIndices;
    int _predStartIndexRandom;
    boolean _randomSlopeToo;
    int[] _fixedCatIndices;
    int _numLevel2Units;
    int _level2UnitIndex; // store column index of level 2 predictor column
    int _predStartIndexFixed;
    public double[] _icc;
    public double _log_likelihood;
    public double _log_likelihood_valid;  // store for frames other than training
    public int _iterations;
    public int _nobs;
    public int _nobs_valid;
    public double _yMinusFixPredSquare;
    public double _yMinusFixPredSquareValid;
    public TwoDimTable _scoring_history_valid;

    /**
     * For debugging only.  Copy over the generated fixed matrices to model._output.
     */
    public void setModelOutputFixMatVec(HGLMTask.ComputationEngineTask comp) {
      _afjtyj = copy2DArray(comp._AfjTYj);
      _arjtyj = copy2DArray(comp._ArjTYj);
      _afjtafj = copy3DArray(comp._AfjTAfj);
      _afjtarj = copy3DArray(comp._AfjTArj);
      _nobs = comp._nobs;
    }
    
    public void setModelOutput(HGLMTask.ComputationEngineTask comp) {
      _randomCatIndices = comp._randomCatIndices;
      _randomNumIndices = comp._randomNumIndices;
      _randomCatArrayStartIndices = comp._randomCatArrayStartIndices;
      _predStartIndexRandom = comp._predStartIndexRandom;
      _randomSlopeToo = !(comp._numRandomCoeffs == 1 && comp._parms._random_intercept);
      _fixedCatIndices = comp._fixedCatIndices;
      _predStartIndexFixed = comp._predStartIndexFixed;
      _arjtarj = copy3DArray(comp._ArjTArj);
      _log_likelihood = Double.NEGATIVE_INFINITY;
    }
    
    public HGLMModelOutput(HGLM b, DataInfo dinfo) {
       super(b, dinfo._adaptedFrame);
       _dinfo = dinfo;
       _domains = dinfo._adaptedFrame.domains();
       _family = b._parms._family;
       _random_family = b._parms._random_family;
    }

    public void setModelOutputFields(ComputationStateHGLM state) {
      _fixed_coefficient_names = state.getFixedCofficientNames();
      _random_coefficient_names = state.getRandomCoefficientNames();
      _group_column_names = state.getGroupColumnNames();
      _tauUVar = state.getTauUVar();
      _tau_e_var = state.getTauEVarE10();
      _tmat = state.getT();
      _num_fixed_coeffs = state.getNumFixedCoeffs();
      _num_random_coeffs = state.getNumRandomCoeffs();
      _numLevel2Units = state.getNumLevel2Units();
      _level2UnitIndex = state.getLevel2UnitIndex();
      _nobs = state._nobs;
      _beta = state.getBeta();
      _ubeta = state.getUbeta();
      _num_random_coeffs = _ubeta[0].length;
      _iterations = state._iter;
    }

    @Override
    public int nclasses() { // only support Gaussian now
      return 1;
    }

    @Override public ModelCategory getModelCategory() {
      return ModelCategory.Regression;
    }
  }
  
  @Override
  protected Futures remove_impl(Futures fs, boolean cascade) {
    super.remove_impl(fs, cascade);
    return fs;
  }

  @Override
  protected AutoBuffer writeAll_impl(AutoBuffer ab) {
    return super.writeAll_impl(ab);
  }

  @Override
  protected Keyed readAll_impl(AutoBuffer ab, Futures fs) {
    return super.readAll_impl(ab, fs);
  }

  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    sb.append(super.toString());
    sb.append(" loglikelihood: "+this._output._log_likelihood);
    sb.append(" fixed effect coefficients: "+ Arrays.toString(this._output._beta));
    int numLevel2 = this._output._ubeta.length;
    for (int index=0; index<numLevel2; index++)
      sb.append(" standard error of random effects for level 2 index " + index + ": "+this._output._tmat[index][index]);
    sb.append(" standard error of residual error: "+this._output._tau_e_var);
    sb.append(" ICC: "+ Arrays.toString(this._output._icc));
    sb.append(" loglikelihood: "+this._output._log_likelihood);
    sb.append(" iterations taken to build model: " + this._output._iterations);
    sb.append(" coefficients for fixed effect: "+Arrays.toString(this._output._beta));
    for (int index=0; index<numLevel2; index++)
      sb.append(" coefficients for random effect for level 2 index: "+index+": "+Arrays.toString(this._output._ubeta[index]));
    return sb.toString();
  }
}
