package water.api.schemas3;

import hex.MultinomialAucType;
import hex.genmodel.utils.DistributionFamily;
import hex.Model;
import hex.ScoreKeeper;
import water.*;
import water.api.API;
import water.api.schemas3.KeyV3.FrameKeyV3;
import water.api.schemas3.KeyV3.ModelKeyV3;
import water.fvec.Frame;
import water.util.ArrayUtils;
import water.util.PojoUtils;

import java.lang.reflect.Field;
import java.util.*;

/**
 * An instance of a ModelParameters schema contains the Model build parameters (e.g., K and max_iterations for KMeans).
 * NOTE: use subclasses, not this class directly.  It is not abstract only so that we can instantiate it to generate metadata
 * for it for the metadata API.
 */
public class ModelParametersSchemaV3<P extends Model.Parameters, S extends ModelParametersSchemaV3<P, S>>
    extends SchemaV3<P, S> {
  ////////////////////////////////////////
  // NOTE:
  // Parameters must be ordered for the UI
  ////////////////////////////////////////
  public String[] fields() {
    try { return (String[]) getClass().getField("fields").get(getClass()); }
    catch (Exception e) { throw H2O.fail("Caught exception from accessing the schema field list", e);  }
  }

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // CAREFUL: This class has its own JSON serializer.  If you add a field here you probably also want to add it to the serializer!
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Parameters common to all models:
  @API(level = API.Level.critical, direction = API.Direction.INOUT,
      help="Destination id for this model; auto-generated if not specified.")
  public ModelKeyV3 model_id;

  @API(level = API.Level.critical, direction = API.Direction.INOUT,
      help = "Id of the training data frame.")
  public FrameKeyV3 training_frame;

  @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
      help = "Id of the validation data frame.")
  public FrameKeyV3 validation_frame;

  @API(level = API.Level.critical, direction = API.Direction.INOUT,
      help = "Number of folds for K-fold cross-validation (0 to disable or >= 2).")
  public int nfolds;

  @API(level = API.Level.expert, direction = API.Direction.INOUT,
      help = "Whether to keep the cross-validation models.")
  public boolean keep_cross_validation_models;

  @API(level = API.Level.expert, direction = API.Direction.INOUT,
      help = "Whether to keep the predictions of the cross-validation models.")
  public boolean keep_cross_validation_predictions;

  @API(level = API.Level.expert, direction = API.Direction.INOUT,
      help = "Whether to keep the cross-validation fold assignment.")
  public boolean keep_cross_validation_fold_assignment;

  @API(help="Allow parallel training of cross-validation models", direction=API.Direction.INOUT, level = API.Level.expert)
  public boolean parallelize_cross_validation;

  @API(help = "Distribution function", values = { "AUTO", "bernoulli", "quasibinomial", "ordinal", "multinomial", "gaussian", "poisson", "gamma", "tweedie", "laplace", "quantile", "huber", "custom" }, level = API.Level.secondary, gridable = true)
  public DistributionFamily distribution;

  @API(level = API.Level.secondary, direction = API.Direction.INPUT, gridable = true,
          help = "Tweedie power for Tweedie regression, must be between 1 and 2.")
  public double tweedie_power;

  @API(level = API.Level.secondary, direction = API.Direction.INPUT, gridable = true,
          help = "Desired quantile for Quantile regression, must be between 0 and 1.")
  public double quantile_alpha;


  @API(help = "Desired quantile for Huber/M-regression (threshold between quadratic and linear loss, must be between 0 and 1).",
          level = API.Level.secondary, direction = API.Direction.INPUT, gridable = true)
  public double huber_alpha;

  @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
      is_member_of_frames = {"training_frame", "validation_frame"},
      is_mutually_exclusive_with = {"ignored_columns"},
      help = "Response variable column.")
  public FrameV3.ColSpecifierV3 response_column;

  @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
      is_member_of_frames = {"training_frame", "validation_frame"},
      is_mutually_exclusive_with = {"ignored_columns", "response_column"},
      help = "Column with observation weights. Giving some observation a weight of zero is equivalent to excluding it" +
          " from the dataset; giving an observation a relative weight of 2 is equivalent to repeating that row twice." +
          " Negative weights are not allowed. Note: Weights are per-row observation weights and do not increase the" +
          " size of the data frame. This is typically the number of times a row is repeated, but non-integer values are" +
          " supported as well. During training, rows with higher weights matter more, due to the larger loss function" +
          " pre-factor. If you set weight = 0 for a row, the returned prediction frame at that row is zero and this" +
          " is incorrect. To get an accurate prediction, remove all rows with weight == 0.")
  public FrameV3.ColSpecifierV3 weights_column;

  @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
      is_member_of_frames = {"training_frame", "validation_frame"},
      is_mutually_exclusive_with = {"ignored_columns","response_column", "weights_column"},
      help = "Offset column. This will be added to the combination of columns before applying the link function.")
  public FrameV3.ColSpecifierV3 offset_column;

  @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
      is_member_of_frames = {"training_frame"},
      is_mutually_exclusive_with = {"ignored_columns", "response_column", "weights_column", "offset_column"},
      help = "Column with cross-validation fold index assignment per observation.")
  public FrameV3.ColSpecifierV3 fold_column;

  @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
      values = {"AUTO", "Random", "Modulo", "Stratified"},
      help = "Cross-validation fold assignment scheme, if fold_column is not specified. The 'Stratified' option will " +
          "stratify the folds based on the response variable, for classification problems.")
  public Model.Parameters.FoldAssignmentScheme fold_assignment;

  @API(level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true,
          values = {"AUTO", "Enum", "OneHotInternal", "OneHotExplicit", "Binary", "Eigen", "LabelEncoder", "SortByResponse", "EnumLimited"},
          help = "Encoding scheme for categorical features")
  public Model.Parameters.CategoricalEncodingScheme categorical_encoding;

  @API(level = API.Level.secondary, direction = API.Direction.INPUT, gridable = true,
      help = "For every categorical feature, only use this many most frequent categorical levels for model training. Only used for categorical_encoding == EnumLimited.")
  public int max_categorical_levels;

  @API(level = API.Level.critical, direction = API.Direction.INOUT,
      is_member_of_frames = {"training_frame", "validation_frame"},
      help = "Names of columns to ignore for training.")
  public String[] ignored_columns;

  @API(level = API.Level.critical, direction = API.Direction.INOUT,
      help = "Ignore constant columns.")
  public boolean ignore_const_cols;

  @API(level = API.Level.secondary, direction = API.Direction.INOUT,
      help = "Whether to score during each iteration of model training.")
  public boolean score_each_iteration;

  /**
   * A model key associated with a previously trained
   * model. This option allows users to build a new model as a
   * continuation of a previously generated model (e.g., by a grid search).
   */
  @API(level = API.Level.secondary, direction=API.Direction.INOUT,
      help = "Model checkpoint to resume training with.")
  public ModelKeyV3 checkpoint;

  /**
   * Early stopping based on convergence of stopping_metric.
   * Stop if simple moving average of length k of the stopping_metric does not improve (by stopping_tolerance) for k=stopping_rounds scoring events."
   * Can only trigger after at least 2k scoring events. Use 0 to disable.
   */
  @API(help = "Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric does not improve for k:=stopping_rounds scoring events (0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public int stopping_rounds;

  @API(help = "Maximum allowed runtime in seconds for model training. Use 0 to disable.", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public double max_runtime_secs;
  
  

  /**
   * Metric to use for convergence checking, only for _stopping_rounds > 0
   */
  @API(help = "Metric to use for early stopping (AUTO: logloss for classification, deviance for regression and " +
          "anomaly_score for Isolation Forest). Note that custom and custom_increasing can only be used in GBM and " +
          "DRF with the Python client.",
          valuesProvider = ModelParamsValuesProviders.StoppingMetricValuesProvider.class,
          level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public ScoreKeeper.StoppingMetric stopping_metric;

  @API(help = "Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public double stopping_tolerance;

  @API(help = "Gains/Lift table number of bins. 0 means disabled.. Default value -1 means automatic binning.",
      level = API.Level.secondary, direction=API.Direction.INOUT)
  public int gainslift_bins;

  /*
   * Custom metric
   */
  @API(help = "Reference to custom evaluation function, format: `language:keyName=funcName`", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = false)
  public String custom_metric_func;

  /*
   * Custom distribution
   */
  @API(help = "Reference to custom distribution, format: `language:keyName=funcName`", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = false)
  public String custom_distribution_func;

  @API(help = "Automatically export generated models to this directory.", level = API.Level.secondary, direction = API.Direction.INOUT)
  public String export_checkpoints_dir;

  @API(help = "Set default multinomial AUC type.",
          valuesProvider = ModelParamsValuesProviders.MultinomialAucTypeSchemeValuesProvider.class,
          level = API.Level.secondary, direction = API.Direction.INOUT, gridable = true)
  public MultinomialAucType auc_type;

  protected static String[] append_field_arrays(String[] first, String[] second) {
    String[] appended = new String[first.length + second.length];
    System.arraycopy(first, 0, appended, 0, first.length);
    System.arraycopy(second, 0, appended, first.length, second.length);
    return appended;
  }

  public S fillFromImpl(P impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES);

    if (impl._train != null) {
      Value v = DKV.get(impl._train);
      if (v != null) {
        training_frame = new FrameKeyV3(((Frame) v.get())._key);
      }
    }

    if (impl._valid != null) {
      Value v = DKV.get(impl._valid);
      if (v != null) {
        validation_frame = new FrameKeyV3(((Frame) v.get())._key);
      }
    }

    return (S)this;
  }

  public P fillImpl(P impl) {
    super.fillImpl(impl);

    impl._train = (this.training_frame == null) ? null : Key.<Frame>make(this.training_frame.name);
    impl._valid = (this.validation_frame == null) ? null : Key.<Frame>make(this.validation_frame.name);

    return impl;
  }

  private static void compute_transitive_closure_of_is_mutually_exclusive(ModelParameterSchemaV3[] metadata) {
    // Form the transitive closure of the is_mutually_exclusive field lists by visiting
    // all fields and collecting the fields in a Map of Sets.  Then pass over them a second
    // time setting the full lists.
    Map<String, Set<String>> field_exclusivity_groups = new HashMap<>();
    for (ModelParameterSchemaV3 param : metadata) {
      String name = param.name;

      // Turn param.is_mutually_exclusive_with into a List which we will walk over twice
      List<String> me = new ArrayList<String>();
      me.add(name);
      // Note: this can happen if this field doesn't have an @API annotation, in which case we got an earlier WARN
      if (param.is_mutually_exclusive_with != null) me.addAll(Arrays.asList(param.is_mutually_exclusive_with));

      // Make a new Set which contains ourselves, fields we have already been connected to,
      // and fields *they* have already been connected to.
      Set<String> new_set = new HashSet<>();
      for (String s : me) {
        // Were we mentioned by a previous field?
        if (field_exclusivity_groups.containsKey(s))
          new_set.addAll(field_exclusivity_groups.get(s));
        else
          new_set.add(s);
      }

      // Now point all the fields in our Set to the Set.
      for (String s : me) {
        field_exclusivity_groups.put(s, new_set);
      }
    }

    // Now walk over all the fields and create new comprehensive is_mutually_exclusive arrays, not containing self.
    for (ModelParameterSchemaV3 param : metadata) {
      String name = param.name;
      Set<String> me = field_exclusivity_groups.get(name);
      Set<String> not_me = new HashSet<>(me);
      not_me.remove(name);
      param.is_mutually_exclusive_with = not_me.toArray(new String[not_me.size()]);
    }
  }

  /**
   * Write the parameters, including their metadata, into an AutoBuffer.  Used by
   * ModelBuilderSchema#writeJSON_impl and ModelSchemaV3#writeJSON_impl.
   */
  public static AutoBuffer writeParametersJSON(AutoBuffer ab, ModelParametersSchemaV3 parameters, ModelParametersSchemaV3 input_parameters, ModelParametersSchemaV3 default_parameters, String name) {
    String[] fields = parameters.fields();

    // Build ModelParameterSchemaV2 objects for each field, and the call writeJSON on the array
    final ModelParameterSchemaV3[] additionalParameters = parameters.getAdditionalParameters();
    ModelParameterSchemaV3[] metadata = new ModelParameterSchemaV3[fields.length];

    String field_name = null;
    try {
      for (int i = 0; i < fields.length; i++) {
        field_name = fields[i];
        Field f = parameters.getClass().getField(field_name);

        // TODO: cache a default parameters schema
        ModelParameterSchemaV3 schema = new ModelParameterSchemaV3(parameters, input_parameters, default_parameters, f);
        metadata[i] = schema;
      }
    } catch (NoSuchFieldException e) {
      throw new RuntimeException("Caught exception accessing field: " + field_name + " for schema object: " + parameters + ": " + e.toString());
    }

    compute_transitive_closure_of_is_mutually_exclusive(metadata);
    if (additionalParameters != null) {
      metadata = ArrayUtils.append(metadata, additionalParameters);
    }
    ab.putJSONA(name, metadata);
    return ab;
  }

  protected ModelParameterSchemaV3[] getAdditionalParameters() {
    return null;
  }

  /**
   * 
   * @param schemaClass   A schema class to extract {@link API} annotated parameters 
   * @param <X> A generic type for a {@link Class} object representing a class extending {@link ModelParametersSchemaV3}. 
   * @return
   */
  protected static <X extends Class<? extends ModelParametersSchemaV3>> List<String> extractDeclaredApiParameters(X schemaClass){
    final Field[] declaredFields = schemaClass.getDeclaredFields();
    
    final List<String> paramsList = new ArrayList<>(declaredFields.length);
    for (Field field : declaredFields){
      if(!field.isAnnotationPresent(API.class)) continue;
      paramsList.add(field.getName());
    }
    
    return paramsList;
  }
}
