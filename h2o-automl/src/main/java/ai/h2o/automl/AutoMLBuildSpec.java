package ai.h2o.automl;

import hex.ScoreKeeper;
import hex.grid.HyperSpaceSearchCriteria.RandomDiscreteValueSearchCriteria;
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
  public static final class AutoMLBuildControl extends Iced {

    public AutoMLBuildControl() {
      stopping_criteria = new AutoMLStoppingCriteria();

      // reasonable defaults:
      stopping_criteria.set_max_models(0);
      stopping_criteria.set_max_runtime_secs(3600);
      stopping_criteria.set_max_model_runtime_secs(0);

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
    public AutoMLStoppingCriteria stopping_criteria;

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

  public static final class AutoMLStoppingCriteria extends Iced {

    public static final int AUTO_STOPPING_TOLERANCE = -1;

    private RandomDiscreteValueSearchCriteria _searchCriteria;
    private double max_model_runtime_secs = 0;

    public AutoMLStoppingCriteria() {
      super();
      _searchCriteria = new RandomDiscreteValueSearchCriteria();
    }

    public double max_model_runtime_secs() {
      return max_model_runtime_secs;
    }

    public void set_max_model_runtime_secs(double max_model_runtime_secs) {
      this.max_model_runtime_secs = max_model_runtime_secs;
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
  static final public class AutoMLBuildModels extends Iced {
    public Algo[] exclude_algos;
    public Algo[] include_algos;
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
