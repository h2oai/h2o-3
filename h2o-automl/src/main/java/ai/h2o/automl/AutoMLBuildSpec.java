package ai.h2o.automl;

import hex.Model;
import hex.ScoreKeeper.StoppingMetric;
import hex.grid.HyperSpaceSearchCriteria;
import water.H2O;
import water.Iced;
import water.Key;
import water.exceptions.H2OIllegalValueException;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.IcedHashMap;
import water.util.Log;
import water.util.PojoUtils;
import water.util.PojoUtils.FieldNaming;

import java.util.ArrayList;
import java.util.List;
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

    public double exploration_ratio = 0.67;
    public String export_checkpoints_dir = null;

    public AutoMLBuildControl() {
      stopping_criteria = new AutoMLStoppingCriteria();
    }
  }

  public static final class AutoMLStoppingCriteria extends Iced {

    public static final int AUTO_STOPPING_TOLERANCE = -1;

    public static double default_stopping_tolerance_for_frame(Frame frame) {
      return HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria.default_stopping_tolerance_for_frame(frame);
    }

    private final HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria _searchCriteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();
    private double _max_runtime_secs_per_model = 0;

    public AutoMLStoppingCriteria() {
      // reasonable defaults:
      set_max_models(0); // no limit
      set_max_runtime_secs(0); // no limit
      set_max_runtime_secs_per_model(0); // no limit

      set_stopping_rounds(3);
      set_stopping_tolerance(AUTO_STOPPING_TOLERANCE);
      set_stopping_metric(StoppingMetric.AUTO);
    }

    public double max_runtime_secs_per_model() {
      return _max_runtime_secs_per_model;
    }

    public void set_max_runtime_secs_per_model(double max_runtime_secs_per_model) {
      _max_runtime_secs_per_model = max_runtime_secs_per_model;
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

    public StoppingMetric stopping_metric() {
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

    public void set_stopping_metric(StoppingMetric stopping_metric) {
      _searchCriteria.set_stopping_metric(stopping_metric);
    }

    public void set_stopping_tolerance(double stopping_tolerance) {
      _searchCriteria.set_stopping_tolerance(stopping_tolerance);
    }

    public void set_default_stopping_tolerance_for_frame(Frame frame) {
      _searchCriteria.set_default_stopping_tolerance_for_frame(frame);
    }

    public HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria getSearchCriteria() {
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
    public String sort_metric = StoppingMetric.AUTO.name();
  }

  /**
   * The specification of the parameters for building models for a single algo (e.g., GBM), including base model parameters and hyperparameter search.
   */
  public static final class AutoMLBuildModels extends Iced {
    public Algo[] exclude_algos;
    public Algo[] include_algos;
    public StepDefinition[] modeling_plan;
    public AutoMLCustomParameters algo_parameters = new AutoMLCustomParameters();
  }

  public static final class AutoMLCustomParameters extends Iced {

    // convenient property to allow us to modify our model (and later grids) definitions
    // and benchmark them without having to rebuild the backend for each change.
    static final String ALGO_PARAMS_ALL_ENABLED = H2O.OptArgs.SYSTEM_PROP_PREFIX + "automl.algo_parameters.all.enabled";

    // let's limit the list of allowed custom parameters by default for now: we can always decide to open this later.
    private static final String[] ALLOWED_PARAMETERS = {
            "monotone_constraints",
//            "ntrees",
    };
    private static final String ROOT_PARAM = "algo_parameters";

    public static final class AutoMLCustomParameter<V> extends Iced {
      private AutoMLCustomParameter(String name, V value) {
        _name = name;
        _value = value;
      }

      private AutoMLCustomParameter(Algo algo, String name, V value) {
        _algo = algo;
        _name = name;
        _value = value;
      }

      private Algo _algo;
      private String _name;
      private V _value;
    }

    public static final class Builder {
      private final transient List<AutoMLCustomParameter> _anyAlgoParams = new ArrayList<>();
      private final transient List<AutoMLCustomParameter> _specificAlgoParams = new ArrayList<>();

      public <V> Builder add(String param, V value) {
        assertParameterAllowed(param);
        _anyAlgoParams.add(new AutoMLCustomParameter<>(param, value));
        return this;
      }

      public <V> Builder add(Algo algo, String param, V value) {
        assertParameterAllowed(param);
        _specificAlgoParams.add(new AutoMLCustomParameter<>(algo, param, value));
        return this;
      }

      /**
       * Builder is necessary here as the custom parameters must be applied in a certain order,
       * and we can't assume that the consumer of this API will add them in the right order.
       * @return a new AutoMLCustomParameters instance with custom parameters properly assigned.
       */
      public AutoMLCustomParameters build() {
        AutoMLCustomParameters instance = new AutoMLCustomParameters();
        // apply "all" scope first, then algo-specific ones.
        for (AutoMLCustomParameter param : _anyAlgoParams) {
          if (!instance.addParameter(param._name, param._value))
            throw new H2OIllegalValueException(param._name, ROOT_PARAM, param._value);
        }
        for (AutoMLCustomParameter param : _specificAlgoParams) {
          if (!instance.addParameter(param._algo, param._name, param._value))
            throw new H2OIllegalValueException(param._name, ROOT_PARAM, param._value);
        }
        return instance;
      }

      private void assertParameterAllowed(String param) {
        if (!Boolean.parseBoolean(System.getProperty(ALGO_PARAMS_ALL_ENABLED, "false"))
                && !ArrayUtils.contains(ALLOWED_PARAMETERS, param))
          throw new H2OIllegalValueException(ROOT_PARAM, param);
      }

    }

    public static Builder create() {
      return new Builder();
    }

    private final IcedHashMap<String, String[]> _algoParameterNames = new IcedHashMap<>(); // stores the parameters names overridden, by algo name
    private final IcedHashMap<String, Model.Parameters> _algoParameters = new IcedHashMap<>(); //stores the parameters values, by algo name

    public boolean hasCustomParams(Algo algo) {
      return _algoParameterNames.get(algo.name()) != null;
    }

    public boolean hasCustomParam(Algo algo, String param) {
      return ArrayUtils.contains(_algoParameterNames.get(algo.name()), param);
    }

    public void applyCustomParameters(Algo algo, Model.Parameters destParams) {
      if (hasCustomParams(algo)) {
        String[] paramNames = getCustomParameterNames(algo);
        String[] onlyParamNames = Stream.of(paramNames).map(p -> "_"+p).toArray(String[]::new);
        PojoUtils.copyProperties(destParams, getCustomizedDefaults(algo), FieldNaming.CONSISTENT, null, onlyParamNames);
      }
    }

    String[] getCustomParameterNames(Algo algo) {
      return _algoParameterNames.get(algo.name());
    }

    Model.Parameters getCustomizedDefaults(Algo algo) {
      if (!_algoParameters.containsKey(algo.name())) {
        Model.Parameters defaults = defaultParameters(algo);
        if (defaults != null) _algoParameters.put(algo.name(), defaults);
      }
      return _algoParameters.get(algo.name());
    }

    private Model.Parameters defaultParameters(Algo algo) {
      return algo.enabled() ? ModelingStepsRegistry.defaultParameters(algo.name()) : null;
    }

    private void addParameterName(Algo algo, String param) {
      if (!_algoParameterNames.containsKey(algo.name())) {
        _algoParameterNames.put(algo.name(), new String[] {param});
      } else {
        String[] names = _algoParameterNames.get(algo.name());
        if (!ArrayUtils.contains(names, param)) {
          _algoParameterNames.put(algo.name(), ArrayUtils.append(names, param));
        }
      }
    }

    private <V> boolean addParameter(String param, V value) {
      boolean added = false;
      for (Algo algo : Algo.values()) {
        added |= addParameter(algo, param, value);
      }
      return added;
    }

    private <V> boolean addParameter(Algo algo, String param, V value) {
      Model.Parameters customParams = getCustomizedDefaults(algo);
      try {
        if (customParams != null
                && (setField(customParams, param, value, FieldNaming.DEST_HAS_UNDERSCORES)
                    || setField(customParams, param, value, FieldNaming.CONSISTENT))) {
          addParameterName(algo, param);
          return true;
        } else {
          Log.debug("Could not set custom param " + param + " for algo " + algo);
          return false;
        }
      } catch (IllegalArgumentException iae) {
        throw new H2OIllegalValueException(param, ROOT_PARAM, value);
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

  public String project() {
    if (build_control.project_name == null) {
      build_control.project_name = "AutoML_"+ projectTimeStampFormat.format(new Date());
    }
    return build_control.project_name;
  }
}
