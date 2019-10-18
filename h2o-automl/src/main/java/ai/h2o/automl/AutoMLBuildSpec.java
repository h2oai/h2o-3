package ai.h2o.automl;

import hex.Model;
import hex.ScoreKeeper;
import hex.deeplearning.DeepLearningModel;
import hex.ensemble.StackedEnsembleModel;
import hex.glm.GLMModel;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
import hex.tree.drf.DRFModel;
import hex.tree.gbm.GBMModel;
import hex.tree.xgboost.XGBoostModel;
import water.Iced;
import water.Key;
import water.api.schemas3.JobV3;
import water.exceptions.H2OIllegalArgumentException;
import water.exceptions.H2OIllegalValueException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.Log;
import water.util.PojoUtils;
import water.util.PojoUtils.FieldNaming;

import java.util.stream.Stream;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Parameters which specify the build (or extension) of an AutoML build job.
 */
public class AutoMLBuildSpec extends Iced {

  private static final DateFormat projectTimeStampFormat = new SimpleDateFormat("yyyyMMdd_HmmssSSS");

  /**
   * The specification of overall build parameters for the AutoML process.
   */
  public static final class AutoMLBuildControl extends Iced {

    public final AutoMLStoppingCriteria stopping_criteria;
    /**
     * Identifier for models that should be grouped together in the leaderboard (e.g., "airlines" and "iris").
     */
    public String project_name = null;

    // Pass through to all algorithms
    public boolean balance_classes = false;
    public float[] class_sampling_factors;
    public float max_after_balance_size = 5.0f;

    public int nfolds = 5;
    public boolean keep_cross_validation_predictions = false;
    public boolean keep_cross_validation_models = false;
    public boolean keep_cross_validation_fold_assignment = false;
    public String export_checkpoints_dir = null;

    public AutoMLBuildControl() {
      stopping_criteria = new AutoMLStoppingCriteria();

      // reasonable defaults:
      stopping_criteria.set_max_models(0);
      stopping_criteria.set_max_runtime_secs(3600);
      stopping_criteria.set_max_runtime_secs_per_model(0);

      stopping_criteria.set_stopping_rounds(3);
      stopping_criteria.set_stopping_tolerance(0.001);
      stopping_criteria.set_stopping_metric(ScoreKeeper.StoppingMetric.AUTO);
    }
  }

  public static final class AutoMLStoppingCriteria extends Iced {

    public static final int AUTO_STOPPING_TOLERANCE = -1;

    private final RandomDiscreteValueSearchCriteria _searchCriteria = new RandomDiscreteValueSearchCriteria();
    private double _max_runtime_secs_per_model = 0;

    public double max_runtime_secs_per_model() {
      return _max_runtime_secs_per_model;
    }

    public void set_max_runtime_secs_per_model(double max_runtime_secs_per_model) {
      this._max_runtime_secs_per_model = max_runtime_secs_per_model;
    }

    public long seed() {
      return _searchCriteria.seed();
    }

    public int max_models() {
      return _searchCriteria.max_models();
    }

    public double max_runtime_secs() {
      return _searchCriteria.max_runtime_secs();
    }

    public int stopping_rounds() {
      return _searchCriteria.stopping_rounds();
    }

    public ScoreKeeper.StoppingMetric stopping_metric() {
      return _searchCriteria.stopping_metric();
    }

    public double stopping_tolerance() {
      return _searchCriteria.stopping_tolerance();
    }

    public void set_seed(long seed) {
      _searchCriteria.set_seed(seed);
    }

    public void set_max_models(int max_models) {
      _searchCriteria.set_max_models(max_models);
    }

    public void set_max_runtime_secs(double max_runtime_secs) {
      _searchCriteria.set_max_runtime_secs(max_runtime_secs);
    }

    public void set_stopping_rounds(int stopping_rounds) {
      _searchCriteria.set_stopping_rounds(stopping_rounds);
    }

    public void set_stopping_metric(ScoreKeeper.StoppingMetric stopping_metric) {
      _searchCriteria.set_stopping_metric(stopping_metric);
    }

    public void set_stopping_tolerance(double stopping_tolerance) {
      _searchCriteria.set_stopping_tolerance(stopping_tolerance);
    }

    public void set_default_stopping_tolerance_for_frame(Frame frame) {
      _searchCriteria.set_default_stopping_tolerance_for_frame(frame);
    }

    public static double default_stopping_tolerance_for_frame(Frame frame) {
      return RandomDiscreteValueSearchCriteria.default_stopping_tolerance_for_frame(frame);
    }

    public RandomDiscreteValueSearchCriteria getSearchCriteria() {
      return _searchCriteria;
    }

  }

  /**
   * The specification of the datasets to be used for the AutoML process.
   * The user can specify a directory path, a file path (including HDFS, s3 or the like),
   * or the ID of an already-parsed Frame in the H2O cluster.  Paths are processed
   * as usual in H2O.
   */
  public static final class AutoMLInput extends Iced {

    public Key<Frame> training_frame;
    public Key<Frame> validation_frame;
    public Key<Frame> blending_frame;
    public Key<Frame> leaderboard_frame;

    public String response_column;
    public String fold_column;
    public String weights_column;
    public String[] ignored_columns;
    public String sort_metric;
  }

  /**
   * The specification of the parameters for building models for a single algo (e.g., GBM), including base model parameters and hyperparameter search.
   */
  public static final class AutoMLBuildModels extends Iced {
    public Algo[] exclude_algos;
    public Algo[] include_algos;
    public StepDefinition[] modeling_plan;
    public final AutoMLCustomParameters algo_parameters = new AutoMLCustomParameters();
  }

  public static final class AutoMLCustomParameters extends Iced {

    // let's limit the list of allowed custom parameters for now: we can always decide to open this later.
    private static final String[] ALLOWED_PARAMETERS = {
            "monotone_constraints",
            "ntrees",
    };
    private static final String ROOT_PARAM = "algo_parameters";

    private final IcedHashMap<String, String[]> algo_parameter_names = new IcedHashMap<>(); // stores the parameters names overridden, by algo name
    private final IcedHashMap<String, Model.Parameters> algo_parameters = new IcedHashMap<>(); //stores the parameters values, by algo name

    public boolean hasCustomParams(Algo algo) {
      return algo_parameter_names.get(algo.name()) != null;
    }

    public boolean hasCustomParam(Algo algo, String param) {
      return ArrayUtils.contains(algo_parameter_names.get(algo.name()), param);
    }

    public <V> AutoMLCustomParameters add(String param, V value) {
      for (Algo algo : Algo.values()) {
        add(algo, param, value);
      }
      return this;
    }

    public <V> AutoMLCustomParameters add(Algo algo, String param, V value) {
      assertParameterAllowed(param);
      Model.Parameters customParams = getCustomParameters(algo);
      try {
        if (setField(customParams, param, value, FieldNaming.DEST_HAS_UNDERSCORES)
                || setField(customParams, param, value, FieldNaming.CONSISTENT)) {
          addParameterName(algo, param);
        } else {
          Log.debug("Could not set custom param " + param + " for algo " + algo);
        }
      } catch (IllegalArgumentException iae) {
        throw new H2OIllegalValueException(param, ROOT_PARAM, value);
      }
      return this;
    }

    public String[] getCustomParameterNames(Algo algo) {
      return algo_parameter_names.get(algo.name());
    }

    public Model.Parameters getCustomParameters(Algo algo) {
      if (!algo_parameters.containsKey(algo.name())) algo_parameters.put(algo.name(), defaultParameters(algo));
      return algo_parameters.get(algo.name());
    }

    public void applyCustomParameters(Algo algo, Model.Parameters destParams) {
      if (hasCustomParams(algo)) {
        String[] paramNames = getCustomParameterNames(algo);
        String[] onlyParamNames = ArrayUtils.append(Stream.of(paramNames).map(p -> "_"+p).toArray(String[]::new), paramNames);
        PojoUtils.copyProperties(destParams, getCustomParameters(algo), FieldNaming.CONSISTENT, null, onlyParamNames);
      }
    }

    private Model.Parameters defaultParameters(Algo algo) {
      switch (algo) {
        case DeepLearning: return new DeepLearningModel.DeepLearningParameters();
        case DRF: return new DRFModel.DRFParameters();
        case GBM: return new GBMModel.GBMParameters();
        case GLM: return new GLMModel.GLMParameters();
        case StackedEnsemble: return new StackedEnsembleModel.StackedEnsembleParameters();
        case XGBoost: return new XGBoostModel.XGBoostParameters();
        default: throw new H2OIllegalArgumentException("Custom parameters are not supported for "+algo.name()+".");
      }
    }

    private void assertParameterAllowed(String param) {
      if (!ArrayUtils.contains(ALLOWED_PARAMETERS, param))
        throw new H2OIllegalValueException(ROOT_PARAM, param);
    }

    private void addParameterName(Algo algo, String param) {
      if (!algo_parameter_names.containsKey(algo.name())) {
        algo_parameter_names.put(algo.name(), new String[] {param});
      } else {
        String[] names = algo_parameter_names.get(algo.name());
        if (!ArrayUtils.contains(names, param)) {
          algo_parameter_names.put(algo.name(), ArrayUtils.append(names, param));
        }
      }
    }

    private <D, V> boolean setField(D dest, String fieldName, V value, FieldNaming naming) {
      try {
        PojoUtils.setField(dest, fieldName, value, naming);
        return true;
      } catch (IllegalArgumentException iae) {
        // propagate exception iff the value was wrong (conversion issue), ignore if the field doesn't exist.
        try {
          PojoUtils.getFieldValue(dest, fieldName, naming);
        } catch (IllegalArgumentException ignored){
          return false;
        }
        throw iae;
      }
    }
  }

  public final AutoMLBuildControl build_control = new AutoMLBuildControl();
  public final AutoMLInput input_spec = new AutoMLInput();
  public final AutoMLBuildModels build_models = new AutoMLBuildModels();

  // output
  public JobV3 job;

  public String project() {
    if (build_control.project_name == null) {
      build_control.project_name = "AutoML_"+ projectTimeStampFormat.format(new Date());
    }
    return build_control.project_name;
  }
}
