package ai.h2o.automl;

import hex.ScoreKeeper;
import hex.grid.HyperSpaceSearchCriteria;
import hex.schemas.GridSearchSchema;
import water.Iced;
import water.Key;
import water.api.schemas3.ImportFilesV3;
import water.api.schemas3.JobV3;
import water.fvec.Frame;
import water.parser.ParseSetup;

/**
 * Parameters which specify the build (or extension) of an AutoML build job.
 */
public class AutoMLBuildSpec extends Iced {

  /**
   * Default constructor provides the default behavior.
   */
  public AutoMLBuildSpec() {
    this.input_spec = new AutoMLInput();
    this.build_control = new AutoMLBuildControl();
    // Note: no defaults for input_spec!
    this.feature_engineering = new AutoMLFeatureEngineering();
    this.build_models = new AutoMLBuildModels ();
    this.ensemble_parameters = new AutoMLEnsembleParameters();
  }

  /**
   * The specification of overall build parameters for the AutoML process.
   */
  static final public class AutoMLBuildControl extends Iced {
    public AutoMLBuildControl() {
      stopping_criteria = new HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria();

      // reasonable defaults:
      stopping_criteria.set_max_runtime_secs(3600);
      stopping_criteria.set_stopping_rounds(3);
      stopping_criteria.set_stopping_tolerance(0.001);
      stopping_criteria.set_stopping_metric(ScoreKeeper.StoppingMetric.AUTO);
    }

    /**
     * Identifier for models that should be grouped together in the leaderboard
     * (e.g., "airlines" and "iris").  If the user doesn't set it we use the basename
     * of the training file name.
     */
    public String project_name = null;
    public String loss = "AUTO";  // TODO: plumb through
    public HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria stopping_criteria;
  }

  /**
   * The specification of the datasets to be used for the AutoML process.
   * The user can specify a directory path, a file path (including HDFS, s3 or the like),
   * or the ID of an already-parsed Frame in the H2O cluster.  Paths are processed
   * as usual in H2O.
   */
  static final public class AutoMLInput extends Iced {
    public ImportFilesV3.ImportFiles training_path;
    public ImportFilesV3.ImportFiles validation_path;
    public ImportFilesV3.ImportFiles leaderboard_path;

    public ParseSetup parse_setup;

    // @API(help="auxiliary relational datasets", direction=API.Direction.INPUT)
    // public String[] datasets_to_join;

    public Key<Frame> training_frame;
    public Key<Frame> validation_frame;
    public Key<Frame> leaderboard_frame;

    public String response_column;
    public String fold_column;
    public String weights_column;
    public String[] ignored_columns;
  }

  /**
   * The specification of automatic feature engineering to be used for the AutoML process.
   */
  static final public class AutoMLFeatureEngineering extends Iced {
    public boolean try_mutations = false;
  }

  /**
   * The specification of the parameters for building models for a single algo (e.g., GBM), including base model parameters and hyperparameter search.
   */
  static final public class AutoMLBuildModels extends Iced {
    public AutoML.algo[] exclude_algos;
    public GridSearchSchema[] model_searches;
  }

  /**
   * The specification of ensemble-building to be used for the AutoML process, if any.  If this object is null, do not build ensembles.
   */
  static final public class AutoMLEnsembleParameters extends Iced {
  }

  public AutoMLBuildControl build_control;
  public AutoMLInput input_spec;
  public AutoMLFeatureEngineering feature_engineering;
  public AutoMLBuildSpec.AutoMLBuildModels build_models;
  public AutoMLEnsembleParameters ensemble_parameters;

  // output
  public JobV3 job;

  private transient String project_cached = null;
  public String project() {
    if (null != project_cached)
      return project_cached;

    // allow the user to override:
    if (null != build_control.project_name) {
      project_cached = build_control.project_name;
      return project_cached;
    }

    String specified = input_spec.training_path != null ?
            input_spec.training_path.path :
            input_spec.training_frame.toString();
    String[] path = specified.split("/");
    project_cached = path[path.length - 1]
            .replace(".hex", "")

            .replace(".CSV", "")
            .replace(".ZIP", "")
            .replace(".GZ", "")
            .replace(".TXT", "")
            .replace(".XLS", "")
            .replace(".XSLX", "")
            .replace(".SVM", "")
            .replace(".SVMLight", "")
            .replace(".ORC", "")
            .replace(".ARFF", "")

            .replace(".csv", "")
            .replace(".zip", "")
            .replace(".gz", "")
            .replace(".txt", "")
            .replace(".xls", "")
            .replace(".xslx", "")
            .replace(".svmlight", "")
            .replace(".svm", "")
            .replace(".orc", "")
            .replace(".arff", "");
    project_cached = "automl_" + project_cached;
    return project_cached;
  }
}
