package water.automl.api.schemas3;


import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLStoppingCriteria;
import hex.ScoreKeeper;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.Schema;
import water.api.schemas3.*;
import water.api.schemas3.ModelParamsValuesProviders.StoppingMetricValuesProvider;
import water.util.JSONUtils;

import water.util.PojoUtils;

import java.util.Map;

import static ai.h2o.automl.AutoMLBuildSpec.AutoMLStoppingCriteria.AUTO_STOPPING_TOLERANCE;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLBuildSpecV99 extends SchemaV3<AutoMLBuildSpec, AutoMLBuildSpecV99> {

  //////////////////////////////////////////////////////
  // Input and output classes used by the build process.
  //////////////////////////////////////////////////////

  /**
   * The specification of overall build parameters for the AutoML process.
   * TODO: this should have all the standard early-stopping functionality like Grid does.
   */
  public static final class AutoMLBuildControlV99 extends Schema<AutoMLBuildSpec.AutoMLBuildControl, AutoMLBuildControlV99> {

    @API(help="Optional project name used to group models from multiple AutoML runs into a single Leaderboard; derived from the training data name if not specified.")
    public String project_name;

    @API(help="Model performance based stopping criteria for the AutoML run.", direction=API.Direction.INPUT)
    public AutoMLStoppingCriteriaV99 stopping_criteria;

    @API(help="Number of folds for k-fold cross-validation (defaults to 5, must be >=2 or use 0 to disable). Disabling prevents Stacked Ensembles from being built.", direction=API.Direction.INPUT)
    public int nfolds;

    @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).", level = API.Level.secondary, direction = API.Direction.INOUT)
    public boolean balance_classes;

    @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
    public float[] class_sampling_factors;

    @API(help = "Maximum relative size of the training data after balancing class counts (defaults to 5.0 and can be less than 1.0). Requires balance_classes.", level = API.Level.expert, direction = API.Direction.INOUT)
    public float max_after_balance_size;

    @API(help="Whether to keep the predictions of the cross-validation predictions.  This needs to be set to TRUE if running the same AutoML object for repeated runs because CV predictions are required to build additional Stacked Ensemble models in AutoML.", direction=API.Direction.INPUT)
    public boolean keep_cross_validation_predictions;

    @API(help="Whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster.", direction=API.Direction.INPUT, gridable = true)
    public boolean keep_cross_validation_models;

    @API(help="Whether to keep cross-validation assignments.", direction=API.Direction.INPUT)
    public boolean keep_cross_validation_fold_assignment;

    @API(help = "Path to a directory where every generated model will be stored.", direction = API.Direction.INOUT)
    public String export_checkpoints_dir;

  } // class AutoMLBuildControlV99

  /**
   * The specification of the datasets to be used for the AutoML process.
   * The user can specify a directory path, a file path (including HDFS, s3 or the like),
   * or the ID of an already-parsed Frame in the H2O cluster.  Only one of these may be specified;
   * if more than one is specified the server will return a 412.  Paths are processed
   * as usual in H2O.
   * <p>
   * The user also specifies the response column and, optionally, an array of columns to ignore.
   */
  public static final class AutoMLInputV99 extends Schema<AutoMLBuildSpec.AutoMLInput, AutoMLInputV99> {

    @API(help = "ID of the training data frame.", direction=API.Direction.INPUT)
    public KeyV3.FrameKeyV3 training_frame;

    @API(help = "ID of the validation data frame (used for early stopping in grid searches and for early stopping of the AutoML process itself).", direction=API.Direction.INPUT)
    public KeyV3.FrameKeyV3 validation_frame;

    @API(help = "ID of the H2OFrame used to train the the metalearning algorithm in Stacked Ensembles (instead of relying on cross-validated predicted values). When provided, it is also recommended to disable cross validation by setting `nfolds=0` and to provide a leaderboard frame for scoring purposes.", direction = API.Direction.INPUT)
    public KeyV3.FrameKeyV3 blending_frame;

    @API(help = "ID of the leaderboard data frame (used to score models and rank them on the AutoML Leaderboard).", direction=API.Direction.INPUT)
    public KeyV3.FrameKeyV3 leaderboard_frame;

    @API(help = "Response column",
            direction=API.Direction.INPUT,
            is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "fold_column", "weights_column"}
      )
    public FrameV3.ColSpecifierV3 response_column;

    @API(help = "Fold column (contains fold IDs) in the training frame. These assignments are used to create the folds for cross-validation of the models.",
            direction=API.Direction.INPUT,
            is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "response_column", "weights_column"}
    )
    public FrameV3.ColSpecifierV3 fold_column;

    @API(help = "Weights column in the training frame, which specifies the row weights used in model training.",
            direction=API.Direction.INPUT,
            is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "response_column", "fold_column"}
    )
    public FrameV3.ColSpecifierV3 weights_column;

    @API(help = "Names of columns to ignore in the training frame when building models.",
         direction=API.Direction.INPUT,
         is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame"},
         is_mutually_exclusive_with = {"response_column", "fold_column", "weights_column"}
      )
    public String[] ignored_columns;

    @API(help="Metric used to sort leaderboard", direction=API.Direction.INPUT)
    public String sort_metric;

  } // class AutoMLInputV99

  public static final class AutoMLStoppingCriteriaV99 extends Schema<AutoMLStoppingCriteria, AutoMLStoppingCriteriaV99> {

    @API(help = "Seed for random number generator; set to a value other than -1 for reproducibility.", direction = API.Direction.INOUT)
    public long seed;

    @API(help = "Maximum number of models to build (optional).", direction = API.Direction.INOUT)
    public int max_models;

    @API(help = "Maximum time to spend building models (optional).", direction = API.Direction.INOUT)
    public double max_runtime_secs;

    @API(help = "Maximum time to spend on a single model (optional).", direction = API.Direction.INOUT)
    public double max_model_runtime_secs;

    @API(help = "Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric does not improve for k:=stopping_rounds scoring events (0 to disable)", level = API.Level.secondary, direction=API.Direction.INOUT)
    public int stopping_rounds;

    @API(help = "Metric to use for early stopping (AUTO: logloss for classification, deviance for regression)", valuesProvider = StoppingMetricValuesProvider.class, level = API.Level.secondary, direction=API.Direction.INOUT)
    public ScoreKeeper.StoppingMetric stopping_metric;

    @API(help = "Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)", level = API.Level.secondary, direction=API.Direction.INOUT)
    public double stopping_tolerance;

    @Override
    public AutoMLStoppingCriteria fillImpl(AutoMLStoppingCriteria impl) {
      AutoMLStoppingCriteria filled = super.fillImpl(impl, new String[] {"_searchCriteria"});
      PojoUtils.copyProperties(filled.getSearchCriteria(), this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES, new String[] {"max_model_runtime_secs"});
      return filled;
    }

    @Override
    public AutoMLStoppingCriteriaV99 fillFromImpl(AutoMLStoppingCriteria impl) {
      AutoMLStoppingCriteriaV99 schema = super.fillFromImpl(impl, new String[]{"_searchCriteria"});
      PojoUtils.copyProperties(schema, impl.getSearchCriteria(), PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] {"max_model_runtime_secs"});
      return schema;
    }
  }

  public static final class AlgoProvider extends EnumValuesProvider<Algo> {
    public AlgoProvider() {
      super(Algo.class);
    }
  }

  public static final class AutoMLBuildModelsV99 extends Schema<AutoMLBuildSpec.AutoMLBuildModels, AutoMLBuildModelsV99> {

    @API(help="A list of algorithms to skip during the model-building phase.", valuesProvider=AlgoProvider.class, direction=API.Direction.INPUT)
    public Algo[] exclude_algos;

    @API(help="A list of algorithms to restrict to during the model-building phase.", valuesProvider=AlgoProvider.class, direction=API.Direction.INPUT)
    public Algo[] include_algos;
  } // class AutoMLBuildModels

  ////////////////
  // Input fields

  @API(help="Specification of overall controls for the AutoML build process.", direction=API.Direction.INPUT)
  public AutoMLBuildControlV99 build_control;

  @API(help="Specification of the input data for the AutoML build process.", direction=API.Direction.INPUT)
  public AutoMLInputV99 input_spec;

  @API(help="If present, specifies details of how to train models.", direction=API.Direction.INPUT)
  public AutoMLBuildModelsV99 build_models;

  ////////////////
  // Output fields
  @API(help="The AutoML Job key", direction=API.Direction.OUTPUT)
  public JobV3 job;


  @Override
  public AutoMLBuildSpecV99 fillFromBody(String body) {
    AutoMLBuildSpecV99 schema = super.fillFromBody(body); //default JSON filling

    // TODO: need to understand why we set stopping tolerance to AUTO iff stopping_criteria is provided without stopping_tolerance
    Map<String, Object> json_body = JSONUtils.parse(body);
    if (json_body.containsKey("build_control")) {
      Map<String, Object> build_control = (Map)json_body.get("build_control");
      if (build_control.containsKey("stopping_criteria")) {
        Map<String, Object> stopping_criteria = (Map)build_control.get("stopping_criteria");

        if (!stopping_criteria.containsKey("stopping_tolerance")) {
          schema.build_control.stopping_criteria.stopping_tolerance = AUTO_STOPPING_TOLERANCE;
        }
      }
    }
    return schema;
  }
}
