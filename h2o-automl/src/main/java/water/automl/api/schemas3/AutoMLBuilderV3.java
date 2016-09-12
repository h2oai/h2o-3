package water.automl.api.schemas3;


import ai.h2o.automl.AutoML;
import water.Iced;
import water.api.API;
import water.api.schemas3.JobV3;
import water.api.schemas3.SchemaV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLBuilderV3 extends SchemaV3<Iced,AutoMLBuilderV3> {

  // Input fields
  @API(help = "path", required = true)
  public String path;

  // Output fields
  @API(help = "files", direction = API.Direction.OUTPUT)
  public String files[];

  @API(help = "names", direction = API.Direction.OUTPUT)
  public String destination_frames[];

  @API(help = "fails", direction = API.Direction.OUTPUT)
  public String fails[];

  @API(help = "dels", direction = API.Direction.OUTPUT)
  public String dels[];


  @API(help="the name of the dataset",direction=API.Direction.INPUT)                 public String dataset_path;
  @API(help="auxiliary relational datasets", direction=API.Direction.INPUT)         public String[] datasets_to_join;
  @API(help="response column by index",direction=API.Direction.INPUT)                public int target_index;
  @API(help="response column by name", direction=API.Direction.INPUT)                public String target_name;
  @API(help="loss function",direction=API.Direction.INPUT)                           public String loss="MSE";
  @API(help="maximum run time in seconds",direction=API.Direction.INPUT)             public long max_time=3600;
  @API(help="Allow AutoML to build ensembles",direction=API.Direction.INPUT)         public boolean ensemble=false;
  @API(help="Prevent AutoML from trying these models",values = {"DL","GLRM","KMEANS","RF","GBM","GLM"},direction=API.Direction.INPUT) public AutoML.algo[] exclude;
  @API(help="Try frame transformations",direction=API.Direction.INPUT)               public boolean try_mutations=false;
  @API(help="The AutoML Job key",direction=API.Direction.OUTPUT)                     public JobV3 job;
}
