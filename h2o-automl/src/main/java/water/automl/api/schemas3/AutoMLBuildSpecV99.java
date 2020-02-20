package water.automl.api.schemas3;


import ai.h2o.automl.Algo;
import ai.h2o.automl.AutoMLBuildSpec;
import ai.h2o.automl.AutoMLBuildSpec.AutoMLStoppingCriteria;
import hex.KeyValue;
import hex.ScoreKeeper.StoppingMetric;
import water.Iced;
import water.api.API;
import water.api.EnumValuesProvider;
import water.api.Schema;
import water.api.ValuesProvider;
import water.api.schemas3.*;
import water.util.*;

import java.util.Arrays;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLBuildSpecV99 extends SchemaV3<AutoMLBuildSpec, AutoMLBuildSpecV99> {

  //////////////////////////////////////////////////////
  // Input and output classes used by the build process.
  //////////////////////////////////////////////////////

  /**
   * The specification of overall build parameters for the AutoML process.
   * TODO: this should have all the standard early-stopping functionality like Grid does.
   */
  public static final class AutoMLBuildControlV99 extends SchemaV3<AutoMLBuildSpec.AutoMLBuildControl, AutoMLBuildControlV99> {

    @API(help="Optional project name used to group models from multiple AutoML runs into a single Leaderboard; derived from the training data name if not specified.",
            direction = API.Direction.INOUT)
    public String project_name;

    @API(help="Model performance based stopping criteria for the AutoML run.")
    public AutoMLStoppingCriteriaV99 stopping_criteria;

    @API(help="Number of folds for k-fold cross-validation (defaults to 5, must be >=2 or use 0 to disable). Disabling prevents Stacked Ensembles from being built.",
            level = API.Level.secondary)
    public int nfolds;

    @API(help = "Balance training data class counts via over/under-sampling (for imbalanced data).",
            level = API.Level.secondary)
    public boolean balance_classes;

    @API(help = "Desired over/under-sampling ratios per class (in lexicographic order). If not specified, sampling factors will be automatically computed to obtain class balance during training. Requires balance_classes.",
            level = API.Level.expert)
    public float[] class_sampling_factors;

    @API(help = "Maximum relative size of the training data after balancing class counts (defaults to 5.0 and can be less than 1.0). Requires balance_classes.",
            level = API.Level.expert)
    public float max_after_balance_size;

    @API(help="Whether to keep the predictions of the cross-validation predictions. "
            + "This needs to be set to TRUE if running the same AutoML object for repeated runs because CV predictions are required to build additional Stacked Ensemble models in AutoML.",
            level = API.Level.expert)
    public boolean keep_cross_validation_predictions;

    @API(help="Whether to keep the cross-validated models. Keeping cross-validation models may consume significantly more memory in the H2O cluster.",
            level = API.Level.expert)
    public boolean keep_cross_validation_models;

    @API(help="Whether to keep cross-validation assignments.",
            level = API.Level.expert)
    public boolean keep_cross_validation_fold_assignment;

    @API(help = "Path to a directory where every generated model will be stored.",
            level = API.Level.expert)
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
  public static final class AutoMLInputV99 extends SchemaV3<AutoMLBuildSpec.AutoMLInput, AutoMLInputV99> {

    @API(help = "ID of the training data frame.")
    public KeyV3.FrameKeyV3 training_frame;

    @API(help = "Response column",
            is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame", "blending_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "fold_column", "weights_column"}
    )
    public FrameV3.ColSpecifierV3 response_column;

    @API(help = "ID of the validation data frame (used for early stopping in grid searches and for early stopping of the AutoML process itself).")
    public KeyV3.FrameKeyV3 validation_frame;

    @API(help = "ID of the H2OFrame used to train the the metalearning algorithm in Stacked Ensembles (instead of relying on cross-validated predicted values)."
            + " When provided, it is also recommended to disable cross validation by setting `nfolds=0` and to provide a leaderboard frame for scoring purposes.")
    public KeyV3.FrameKeyV3 blending_frame;

    @API(help = "ID of the leaderboard data frame (used to score models and rank them on the AutoML Leaderboard).")
    public KeyV3.FrameKeyV3 leaderboard_frame;

    @API(help = "Fold column (contains fold IDs) in the training frame. These assignments are used to create the folds for cross-validation of the models.",
            level = API.Level.secondary,
            is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "response_column", "weights_column"}
    )
    public FrameV3.ColSpecifierV3 fold_column;

    @API(help = "Weights column in the training frame, which specifies the row weights used in model training.",
            level = API.Level.secondary,
            is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame"},
            is_mutually_exclusive_with = {"ignored_columns", "response_column", "fold_column"}
    )
    public FrameV3.ColSpecifierV3 weights_column;

    @API(help = "Names of columns to ignore in the training frame when building models.",
         level = API.Level.secondary,
         is_member_of_frames = {"training_frame", "validation_frame", "leaderboard_frame", "blending_frame"},
         is_mutually_exclusive_with = {"response_column", "fold_column", "weights_column"}
      )
    public String[] ignored_columns;

    @API(help="Metric used to sort leaderboard",
            valuesProvider = AutoMLMetricProvider.class,
            level = API.Level.secondary)
    public String sort_metric;

  } // class AutoMLInputV99

  public static final class AutoMLStoppingCriteriaV99 extends SchemaV3<AutoMLStoppingCriteria, AutoMLStoppingCriteriaV99> {

    @API(help = "Seed for random number generator; set to a value other than -1 for reproducibility.",
            level = API.Level.secondary)
    public long seed;

    @API(help = "Maximum number of models to build (optional).",
            level = API.Level.secondary)
    public int max_models;

    @API(help = "This argument specifies the maximum time that the AutoML process will run for, prior to training the final Stacked Ensemble models. If neither max_runtime_secs nor max_models are specified by the user, then max_runtime_secs defaults to 3600 seconds (1 hour).",
            level = API.Level.secondary)
    public double max_runtime_secs;

    @API(help = "Maximum time to spend on each individual model (optional).",
            level = API.Level.secondary)
    public double max_runtime_secs_per_model;

    @API(help = "Early stopping based on convergence of stopping_metric. Stop if simple moving average of length k of the stopping_metric does not improve for k:=stopping_rounds scoring events (0 to disable)",
            level = API.Level.secondary)
    public int stopping_rounds;

    @API(help = "Metric to use for early stopping (AUTO: logloss for classification, deviance for regression)",
            valuesProvider = AutoMLMetricProvider.class,
            level = API.Level.secondary)
    public StoppingMetric stopping_metric;

    @API(help = "Relative tolerance for metric-based stopping criterion (stop if relative improvement is not at least this much)",
            level = API.Level.secondary)
    public double stopping_tolerance;

    @Override
    public AutoMLStoppingCriteria fillImpl(AutoMLStoppingCriteria impl) {
      AutoMLStoppingCriteria filled = super.fillImpl(impl, new String[] {"_searchCriteria"});
      PojoUtils.copyProperties(filled.getSearchCriteria(), this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES, new String[] {"max_runtime_secs_per_model"});
      PojoUtils.copyProperties(filled.getSearchCriteria().stoppingCriteria(), this, PojoUtils.FieldNaming.DEST_HAS_UNDERSCORES, new String[] {"max_runtime_secs_per_model"});
      return filled;
    }

    @Override
    public AutoMLStoppingCriteriaV99 fillFromImpl(AutoMLStoppingCriteria impl) {
      AutoMLStoppingCriteriaV99 schema = super.fillFromImpl(impl, new String[]{"_searchCriteria"});
      PojoUtils.copyProperties(schema, impl.getSearchCriteria(), PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] {"max_runtime_secs_per_model"});
      PojoUtils.copyProperties(schema, impl.getSearchCriteria().stoppingCriteria(), PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] {"max_runtime_secs_per_model"});
      return schema;
    }
  }

  public static final class AlgoProvider extends EnumValuesProvider<Algo> {
    public AlgoProvider() {
      super(Algo.class);
    }
  }

  public static final class AutoMLMetricProvider extends EnumValuesProvider<StoppingMetric> {
    public AutoMLMetricProvider() {
      // list all metrics currently supported in leaderboard, and by all algos used in AutoML, incl. corresponding gris searches.
      super(StoppingMetric.class, e -> Arrays.asList(
              StoppingMetric.AUTO,
              StoppingMetric.AUC,
              StoppingMetric.AUCPR,
              StoppingMetric.deviance,
              StoppingMetric.lift_top_group,
              StoppingMetric.logloss,
              StoppingMetric.MAE,
              StoppingMetric.mean_per_class_error,
              StoppingMetric.misclassification,
              StoppingMetric.MSE,
              StoppingMetric.RMSE,
              StoppingMetric.RMSLE
      ).contains(e));
    }
  }

  public static final class ScopeProvider implements ValuesProvider {
    private static final String ANY_ALGO = "any";
    private static final String[] SCOPES = ArrayUtils.append(new String[]{ANY_ALGO}, new AlgoProvider().values());

    @Override
    public String[] values() {
      return SCOPES;
    }
  }

  public static final class AutoMLCustomParameterV99<V> extends Schema<Iced, AutoMLCustomParameterV99<V>> {
    @API(help="Scope of application of the parameter (specific algo, or any algo).",
            valuesProvider=ScopeProvider.class)
    public String scope;

    @API(help="Name of the model parameter.")
    public String name;

    @API(help="Value of the model parameter.")
    public JSONValue value;

    @SuppressWarnings("unchecked")
    V getFormattedValue() {
      switch (name) {
        case "monotone_constraints":
          return (V)value.valueAsArray(KeyValue[].class, KeyValueV3[].class);
        default:
          return (V)value.value();
      }
    }
  }

  public static final class AutoMLBuildModelsV99 extends SchemaV3<AutoMLBuildSpec.AutoMLBuildModels, AutoMLBuildModelsV99> {

    @API(help="A list of algorithms to skip during the model-building phase.",
            valuesProvider=AlgoProvider.class,
            level = API.Level.secondary)
    public Algo[] exclude_algos;

    @API(help="A list of algorithms to restrict to during the model-building phase.",
            valuesProvider=AlgoProvider.class,
            level = API.Level.secondary)
    public Algo[] include_algos;

    @API(help="The list of modeling steps to be used by the AutoML engine (they may not all get executed, depending on other constraints).",
            level = API.Level.expert)
    public StepDefinitionV99[] modeling_plan;

    @API(help="Custom algorithm parameters.",
            level = API.Level.expert)
    public AutoMLCustomParameterV99[] algo_parameters;

    @API(help = "A mapping representing monotonic constraints. Use +1 to enforce an increasing constraint and -1 to specify a decreasing constraint.",
            level = API.Level.secondary)
    public KeyValueV3[] monotone_constraints;

    @Override
    public AutoMLBuildSpec.AutoMLBuildModels fillImpl(AutoMLBuildSpec.AutoMLBuildModels impl) {
      super.fillImpl(impl, new String[]{"algo_parameters"});

      if (monotone_constraints != null) {
        AutoMLCustomParameterV99 mc = new AutoMLCustomParameterV99();
        mc.scope = ScopeProvider.ANY_ALGO;
        mc.name = "monotone_constraints";
        mc.value = JSONValue.fromValue(monotone_constraints);
        if (algo_parameters == null) {
          algo_parameters = new AutoMLCustomParameterV99[] {mc};
        } else {
          algo_parameters = ArrayUtils.append(algo_parameters, mc);
        }
      }

      if (algo_parameters != null) {
         AutoMLBuildSpec.AutoMLCustomParameters.Builder builder = AutoMLBuildSpec.AutoMLCustomParameters.create();
        for (AutoMLCustomParameterV99 param : algo_parameters) {
          if (ScopeProvider.ANY_ALGO.equals(param.scope)) {
            builder.add(param.name, param.getFormattedValue());
          } else {
            Algo algo = EnumUtils.valueOf(Algo.class, param.scope);
            builder.add(algo, param.name, param.getFormattedValue());
          }
        }
        impl.algo_parameters = builder.build();
      }
      return impl;
    }

    @Override
    public AutoMLBuildModelsV99 fillFromImpl(AutoMLBuildSpec.AutoMLBuildModels impl) {
      return super.fillFromImpl(impl, new String[]{"algo_parameters"});
    }
  } // class AutoMLBuildModels

  ////////////////
  // Input fields

  @API(help="Specification of overall controls for the AutoML build process.")
  public AutoMLBuildControlV99 build_control;

  @API(help="Specification of the input data for the AutoML build process.")
  public AutoMLInputV99 input_spec;

  @API(help="If present, specifies details of how to train models.")
  public AutoMLBuildModelsV99 build_models;

  ////////////////
  // Output fields
  @API(help="The AutoML Job key",
          direction=API.Direction.OUTPUT)
  public JobV3 job;

  @Override
  public AutoMLBuildSpec fillImpl(AutoMLBuildSpec impl) {
    return super.fillImpl(impl, new String[] {"job"});
  }
}
