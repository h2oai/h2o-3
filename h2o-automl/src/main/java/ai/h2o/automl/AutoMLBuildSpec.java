package ai.h2o.automl;

import hex.ScoreKeeper;
import hex.grid.HyperSpaceSearchCriteria;
import water.Iced;
import water.Key;
import water.api.schemas3.JobV3;
import water.fvec.Frame;

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
    this.build_models = new AutoMLBuildModels();
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
    public HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria stopping_criteria;

    // Pass through to all algorithms
    public boolean balance_classes = false;
    public float[] class_sampling_factors;
    public float max_after_balance_size = 5.0f;

    public int nfolds = 5;
    public boolean keep_cross_validation_predictions = false;
    public boolean keep_cross_validation_models = false;
    public boolean keep_cross_validation_fold_assignment = false;
    public String export_checkpoints_dir = null;
  }

  /**
   * The specification of the datasets to be used for the AutoML process.
   * The user can specify a directory path, a file path (including HDFS, s3 or the like),
   * or the ID of an already-parsed Frame in the H2O cluster.  Paths are processed
   * as usual in H2O.
   */
  static final public class AutoMLInput extends Iced {

    public Key<Frame> training_frame;
    public Key<Frame> validation_frame;
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
  static final public class AutoMLBuildModels extends Iced {
    public AutoML.algo[] exclude_algos;
  }

  public AutoMLBuildControl build_control;
  public AutoMLInput input_spec;
  public AutoMLBuildSpec.AutoMLBuildModels build_models;

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
    project_cached = "automl_" + project_cached;
    return project_cached;
  }
}
