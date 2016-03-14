package water.automl;


import ai.h2o.automl.AutoML;
import water.Iced;
import water.api.API;
import water.api.JobV3;
import water.api.RequestSchema;

public class AutoMLBuilderV3 extends RequestSchema<Iced,AutoMLBuilderV3> {
  @API(help="the name of the dataset",direction=API.Direction.INPUT)                 public String dataset;
  @API(help="response column by index",direction=API.Direction.INPUT)                public int targetIndex;
  @API(help="response column by name", direction=API.Direction.INPUT)                public String targetName;
  @API(help="loss function",direction=API.Direction.INPUT)                           public String loss="MSE";
  @API(help="maximum run time in seconds",direction=API.Direction.INPUT)             public long maxTime=3600;
  @API(help="Allow AutoML to build ensembles",direction=API.Direction.INPUT)         public boolean ensemble=false;
  @API(help="Prevent AutoML from trying these models",values = {"DL","GLRM","KMEANS","RF","GBM","GLM"},direction=API.Direction.INPUT) public AutoML.models[] exclude;
  @API(help="Try frame transformations",direction=API.Direction.INPUT)               public boolean tryMutations=false;
  @API(help="The AutoML Job key",direction=API.Direction.OUTPUT)                     public JobV3 job;
}
