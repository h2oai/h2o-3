package water.automl.api.schemas3;


import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import water.api.API;
import water.api.Schema;
import water.api.schemas3.*;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLBuildSpecV3 extends SchemaV3<AutoMLBuildSpec, AutoMLBuildSpecV3> {

  //////////////////////////////////////////////////////
  // Input and output classes used by the build process.
  //////////////////////////////////////////////////////

  /**
   * The specification of the datasets to be used for the AutoML process.
   * The user can specify a directory path, a file path (including HDFS, s3 or the like),
   * or the ID of an already-parsed Frame in the H2O cluster.  Only one of these may be specified;
   * if more than one is specified the server will return a 412.  Paths are processed
   * as usual in H2O.
   * <p>
   * The user also specifies the response column and, optionally, an array of columns to ignore.
   */
  static final public class AutoMLInputV3 extends Schema<AutoMLBuildSpec.AutoMLInput, AutoMLInputV3> {
    public AutoMLInputV3() { super(); }

    @API(help = "Path of training data to import and parse, in any form that H2O accepts, including local files or directories, s3, hdfs, etc.", required = false)
    public ImportFilesV3 training_path;

    @API(help = "Path of validation data to import and parse, in any form that H2O accepts, including local files or directories, s3, hdfs, etc.", required = false)
    public ImportFilesV3 validation_path;

    @API(help = "Used to override default settings for training and test data parsing.", required = false)
    public ParseSetupV3 parse_setup;

    // @API(help="auxiliary relational datasets", direction=API.Direction.INPUT)
    // public String[] datasets_to_join;

    @API(help = "Id of the training data frame.", required = false)
    public KeyV3.FrameKeyV3 training_frame;

    @API(help = "Id of the validation data frame.", required = false)
    public KeyV3.FrameKeyV3 validation_frame;

    @API(help = "Response variable column.",
            is_member_of_frames = {"training_frame", "validation_frame"},
            is_mutually_exclusive_with = {"ignored_columns"}
            )
    public FrameV3.ColSpecifierV3 response_column;

    @API(help = "Names of columns to ignore for training.",
            is_member_of_frames = {"training_frame", "validation_frame"}
            )
    public String[] ignored_columns;
  } // class AutoMLInputV3


  ////////////////
  // Input fields

  @API(help="Specification of the input data for the AutoML build process")
  public AutoMLInputV3 input_spec;

  @API(help="loss function",direction=API.Direction.INPUT)
  public String loss="MSE";

  @API(help="maximum run time in seconds",direction=API.Direction.INPUT)
  public long max_time=3600;

  @API(help="Allow AutoML to build ensembles",direction=API.Direction.INPUT)
  public boolean ensemble=false;

  @API(help="Prevent AutoML from trying these models",values = {"DL","GLRM","KMEANS","RF","GBM","GLM"},direction=API.Direction.INPUT)
  public AutoML.algo[] exclude;

  @API(help="Try frame transformations",direction=API.Direction.INPUT)
  public boolean try_mutations=false;

  ////////////////
  // Output fields
  @API(help="The AutoML Job key",direction=API.Direction.OUTPUT)
  public JobV3 job;

}
