package ai.h2o.automl;

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
   * The specification of overall build parameters for the AutoML process.
   */
  static final public class AutoMLBuildControl extends Iced {
    public String loss = "MSE";
    public long max_time = 3600;
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


  public AutoMLBuildControl build_control;
  public AutoMLInput input_spec;
  public AutoMLFeatureEngineering feature_engineering;

  // ensembling
  public boolean ensemble = false;

  // model build
  public AutoML.algo[] exclude;

  // output
  public JobV3 job;
}
