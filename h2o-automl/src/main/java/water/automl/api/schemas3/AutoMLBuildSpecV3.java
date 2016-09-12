package water.automl.api.schemas3;


import ai.h2o.automl.AutoML;
import ai.h2o.automl.AutoMLBuildSpec;
import water.api.API;
import water.api.schemas3.FrameV3;
import water.api.schemas3.JobV3;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLBuildSpecV3 extends SchemaV3<AutoMLBuildSpec, AutoMLBuildSpecV3> {

  ////////////////
  // Input fields
  @API(help = "path")
  public String path;

  @API(help="the name of the dataset",direction=API.Direction.INPUT)
  public String dataset;

  @API(help="auxiliary relational datasets", direction=API.Direction.INPUT)
  public String[] datasets_to_join;

  @API(level = API.Level.critical, direction = API.Direction.INOUT,
          help = "Id of the training data frame (Not required, to allow initial validation of model parameters).")
  public KeyV3.FrameKeyV3 training_frame;

  @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
          help = "Id of the validation data frame.")
  public KeyV3.FrameKeyV3 validation_frame;

  @API(level = API.Level.critical, direction = API.Direction.INOUT, gridable = true,
          is_member_of_frames = {"training_frame", "validation_frame"},
          is_mutually_exclusive_with = {"ignored_columns"},
          help = "Response variable column.")
  public FrameV3.ColSpecifierV3 response_column;

  @API(level = API.Level.critical, direction = API.Direction.INOUT,
          is_member_of_frames = {"training_frame", "validation_frame"},
          help = "Names of columns to ignore for training.")
  public String[] ignored_columns;

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
  @API(help = "files", direction = API.Direction.OUTPUT)
  public String files[];

  @API(help = "names", direction = API.Direction.OUTPUT)
  public String destination_frames[];

  @API(help = "fails", direction = API.Direction.OUTPUT)
  public String fails[];

  @API(help = "dels", direction = API.Direction.OUTPUT)
  public String dels[];

  @API(help="The AutoML Job key",direction=API.Direction.OUTPUT)
  public JobV3 job;

}
