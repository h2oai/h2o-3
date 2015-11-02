package water.api;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import hex.Model;
import hex.ScoreKeeper;
import water.AutoBuffer;
import water.DKV;
import water.H2O;
import water.Key;
import water.Value;
import water.api.KeyV3.FrameKeyV3;
import water.api.KeyV3.ModelKeyV3;
import water.fvec.Frame;
import water.util.PojoUtils;

/**
 * An instance of a ModelParameters schema contains the Model build parameters (e.g., K and max_iterations for KMeans).
 * NOTE: use subclasses, not this class directly.  It is not abstract only so that we can instantiate it to generate metadata
 * for it for the metadata API.
 */
public class ModelParametersSchema<P extends Model.Parameters, S extends ModelParametersSchema<P, S>> extends Schema<P, S> {
  ////////////////////////////////////////
  // NOTE:
  // Parameters must be ordered for the UI
  ////////////////////////////////////////

		public String[] fields() {
				Class<? extends ModelParametersSchema> this_clz = this.getClass();
				try {
				    return (String[]) this_clz.getField("fields").get(this_clz);
				}
				catch (Exception e) {
						throw H2O.fail("Caught exception from accessing the schema field list for: " + this);
				}
		}

  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////
  // CAREFUL: This class has its own JSON serializer.  If you add a field here you probably also want to add it to the serializer!
  ////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

  // Parameters common to all models:
  @API(help="Destination id for this model; auto-generated if not specified", required = false, direction=API.Direction.INOUT)
  public ModelKeyV3 model_id;

  @API(help="Training frame", direction=API.Direction.INOUT /* Not required, to allow initial params validation: , required=true */)
  public FrameKeyV3 training_frame;

  @API(help="Validation frame", direction=API.Direction.INOUT, gridable = true)
  public FrameKeyV3 validation_frame;

  @API(help="Number of folds for N-fold cross-validation", level = API.Level.critical, direction= API.Direction.INOUT)
  public int nfolds;

  @API(help="Keep cross-validation model predictions", level = API.Level.expert, direction=API.Direction.INOUT)
  public boolean keep_cross_validation_predictions;

  @API(help = "Response column", is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns"}, direction = API.Direction.INOUT, gridable = true)
  public FrameV3.ColSpecifierV3 response_column;

  @API(help = "Column with observation weights", level = API.Level.secondary, is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns","response_column"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV3 weights_column;

  @API(help = "Offset column", level = API.Level.secondary, is_member_of_frames = {"training_frame", "validation_frame"}, is_mutually_exclusive_with = {"ignored_columns","response_column", "weights_column"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV3 offset_column;

  @API(help = "Column with cross-validation fold index assignment per observation", level = API.Level.secondary, is_member_of_frames = {"training_frame"}, is_mutually_exclusive_with = {"ignored_columns","response_column", "weights_column", "offset_column"}, direction = API.Direction.INOUT)
  public FrameV3.ColSpecifierV3 fold_column;

  @API(help="Cross-validation fold assignment scheme, if fold_column is not specified", values = {"AUTO", "Random", "Modulo", "Stratified"}, level = API.Level.secondary, direction=API.Direction.INOUT)
  public Model.Parameters.FoldAssignmentScheme fold_assignment;

  @API(help="Ignored columns", is_member_of_frames={"training_frame", "validation_frame"}, direction=API.Direction.INOUT)
  public String[] ignored_columns;         // column names to ignore for training

  @API(help="Ignore constant columns", direction=API.Direction.INOUT)
  public boolean ignore_const_cols;

  @API(help="Whether to score during each iteration of model training", direction=API.Direction.INOUT, level = API.Level.secondary)
  public boolean score_each_iteration;

  /**
   * A model key associated with a previously trained
   * model. This option allows users to build a new model as a
   * continuation of a previously generated model (e.g., by a grid search).
   */
  @API(help = "Model checkpoint to resume training with", level = API.Level.secondary, direction=API.Direction.INOUT)
  public ModelKeyV3 checkpoint;

  /**
   * Early stopping based on convergence of stopping_metric.
   * Stop if simple moving average of length k of the stopping_metric does not improve (by stopping_tolerance) for k=stopping_rounds scoring events."
   * Can only trigger after at least 2k scoring events. Use 0 to disable.
   */
  @API(help = "Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric does not improve for k:=stopping_rounds scoring events (0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public int stopping_rounds;

  /**
   * Metric to use for convergence checking, only for _stopping_rounds > 0
   */
  @API(help = "Metric to use for early stopping (AUTO: logloss for classification, deviance for regression)", values = {"AUTO", "deviance", "logloss", "MSE", "AUC", "r2", "misclassification"}, level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public ScoreKeeper.StoppingMetric stopping_metric;

  @API(help = "Relative tolerance for metric-based stopping criterion Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)", level = API.Level.secondary, direction=API.Direction.INOUT, gridable = true)
  public double stopping_tolerance;

  protected static String[] append_field_arrays(String[] first, String[] second) {
    String[] appended = new String[first.length + second.length];
    System.arraycopy(first, 0, appended, 0, first.length);
    System.arraycopy(second, 0, appended, first.length, second.length);
    return appended;
  }

  public S fillFromImpl(P impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES );

    if (null != impl._train) {
      Value v = DKV.get(impl._train);
      if (null != v) {
        training_frame = new FrameKeyV3(((Frame) v.get())._key);
      }
    }

    if (null != impl._valid) {
      Value v = DKV.get(impl._valid);
      if (null != v) {
        validation_frame = new FrameKeyV3(((Frame) v.get())._key);
      }
    }

    return (S)this;
  }

  public P fillImpl(P impl) {
    super.fillImpl(impl);

    impl._train = (null == this.training_frame ? null : Key.<Frame>make(this.training_frame.name));
    impl._valid = (null == this.validation_frame ? null : Key.<Frame>make(this.validation_frame.name));

    return impl;
  }

  private static void compute_transitive_closure_of_is_mutually_exclusive(ModelParameterSchemaV3[] metadata) {
    // Form the transitive closure of the is_mutually_exclusive field lists by visiting
    // all fields and collecting the fields in a Map of Sets.  Then pass over them a second
    // time setting the full lists.
    Map<String, Set<String>> field_exclusivity_groups = new HashMap<>();
    for (int i = 0; i < metadata.length; i++) {
      ModelParameterSchemaV3 param = metadata[i];
      String name = param.name;

      // Turn param.is_mutually_exclusive_with into a List which we will walk over twice
      List<String> me = new ArrayList<String>();
      me.add(name);
      // Note: this can happen if this field doesn't have an @API annotation, in which case we got an earlier WARN
      if (null != param.is_mutually_exclusive_with) me.addAll(Arrays.asList(param.is_mutually_exclusive_with));

      // Make a new Set which contains ourselves, fields we have already been connected to,
      // and fields *they* have already been connected to.
      Set new_set = new HashSet();
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
    for (int i = 0; i < metadata.length; i++) {
      ModelParameterSchemaV3 param = metadata[i];
      String name = param.name;
      Set<String> me = field_exclusivity_groups.get(name);
      Set<String> not_me = new HashSet(me);
      not_me.remove(name);
      param.is_mutually_exclusive_with = not_me.toArray(new String[not_me.size()]);
    }
  }

  /**
   * Write the parameters, including their metadata, into an AutoBuffer.  Used by
   * ModelBuilderSchema#writeJSON_impl and ModelSchema#writeJSON_impl.
   */
  public static final AutoBuffer writeParametersJSON( AutoBuffer ab, ModelParametersSchema parameters, ModelParametersSchema default_parameters) {
    String[] fields = parameters.fields();

    // Build ModelParameterSchemaV2 objects for each field, and the call writeJSON on the array
    ModelParameterSchemaV3[] metadata = new ModelParameterSchemaV3[fields.length];

    String field_name = null;
    try {
      for (int i = 0; i < fields.length; i++) {
        field_name = fields[i];
        Field f = parameters.getClass().getField(field_name);

        // TODO: cache a default parameters schema
        ModelParameterSchemaV3 schema = new ModelParameterSchemaV3(parameters, default_parameters, f);
        metadata[i] = schema;
      }
    } catch (NoSuchFieldException e) {
      throw H2O.fail("Caught exception accessing field: " + field_name + " for schema object: " + parameters + ": " + e.toString());
    }

    compute_transitive_closure_of_is_mutually_exclusive(metadata);

    ab.putJSONA("parameters", metadata);
    return ab;
  }
}
