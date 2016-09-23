package ai.h2o.automl;

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
    public String loss = "MSE";  // TODO: Auto
    public long max_time = 3600; // TODO: same early stopping criteria as RandomDiscreteValueSearchCriteria
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

    public ParseSetup parse_setup;

    // @API(help="auxiliary relational datasets", direction=API.Direction.INPUT)
    // public String[] datasets_to_join;

    public Key<Frame> training_frame;
    public Key<Frame> validation_frame;

    public String response_column;
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
}
