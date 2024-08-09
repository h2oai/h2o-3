package hex.hglm;

import hex.DataInfo;
import hex.Model;
import hex.ModelMetrics;
import hex.deeplearning.DeepLearningModel;
import hex.glm.GLM;
import hex.glm.GLMModel;
import water.AutoBuffer;
import water.Futures;
import water.Key;
import water.Keyed;
import water.fvec.Frame;

import java.io.Serializable;

import static hex.glm.GLMModel.GLMParameters.Family.gaussian;
import static hex.hglm.HGLMModel.HGLMParameters.Method.EM;

public class HGLMModel extends Model<HGLMModel, HGLMModel.HGLMParameters, HGLMModel.HGLMModelOutput> {

  /**
   * Full constructor
   *
   * @param selfKey
   * @param parms
   * @param output
   */
  public HGLMModel(Key<HGLMModel> selfKey, HGLMParameters parms, HGLMModelOutput output) {
    super(selfKey, parms, output);
  }

  @Override
  public ModelMetrics.MetricBuilder makeMetricBuilder(String[] domain) {
    return null;
  }

  @Override
  protected double[] score0(double[] data, double[] preds) {
    return new double[0];
  }

  public static class HGLMParameters extends Model.Parameters {
    public GLMModel.GLMParameters.Family _family = gaussian;
    public GLMModel.GLMParameters.Family _random_family = gaussian;
    public Method _method = EM;
    public double[] _initial_fixed_effects; // initial values of fixed coefficients
    public Serializable _missing_values_handling = GLMModel.GLMParameters.MissingValuesHandling.MeanImputation;
    public int _max_iterations = -1;
    public boolean _random_intercept = true;
    public Key<Frame> _plug_values = null;
    public boolean _generate_scoring_history = false;
    public boolean _remove_collinear_columns = false;
    public String[] _random_columns;  // store predictors that have random components in the coefficients
    public String _group_column;
    public boolean _use_all_factor_levels = false;
    public boolean _standardize = true;
    public double _tau_u_var_init = 0;  // initial random coefficient effects variance estimate, set by user
    public double _tau_e_var_init = 0;   // initial random noise variance estimate, set by user
    public Key _initial_random_effects; // frame key that contains the initial starting values of random coefficient effects
    public long _seed = -1;
    public double _objective_epsilon = 1e-6;
    public double _beta_epsilon = 1e-6;
    
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
    public String[] _fixed_coefficient_names; // include intercept as a coeff name
    public String[] _random_coefficient_names;  // include intercept only if _parms._random_intercept = true
    public String[] _group_column_names;
    public long _training_time_ms;
    double[] _beta;   // fixed coefficients
    double[][] _ubeta;  // random coefficients
    double[] _beta_normalized;
    double[][] _ubeta_normalized;
    double _tauUVar;
    double _tauEVar;
    
    
    public HGLMModelOutput(HGLM b, DataInfo dinfo) {
       super(b, dinfo._adaptedFrame);
       _dinfo = dinfo;
       _domains = dinfo._adaptedFrame.domains();
       _family = b._parms._family;
       _random_family = b._parms._random_family;
    }
    
    public void setModelOutputFields(ComputationStateHGLM state) {
      _beta = state.get_beta();
      _ubeta = state.get_ubeta();
      _fixed_coefficient_names = state.get_fixedCofficientNames();
      _random_coefficient_names = state.get_randomCoefficientNames();
      _group_column_names = state.get_groupColumnNames();
      _tauUVar = state.get_tauUVar();
      _tauEVar = state.get_tauEVar();
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
}
