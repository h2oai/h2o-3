package water.automl;


import water.Job;
import water.api.API;

public class AutoMLBuilderV3 {
  @API(help="the name of the dataset",direction=API.Direction.INPUT)                 public String dataset;
  @API(help="response column",direction=API.Direction.INPUT)                         public int response;
  @API(help="loss function",direction=API.Direction.INPUT)                           public String loss;
  @API(help="maximum run time in seconds",direction=API.Direction.INPUT)             public long maxTime;
  @API(help="Allow AutoML to build ensembles",direction=API.Direction.INPUT)         public boolean ensemble;
  @API(help="Prevent AutoML from trying these models",direction=API.Direction.INPUT) public String[] exclude;
  @API(help="Try frame transformations",direction=API.Direction.INPUT)               public boolean tryMutations;
  @API(help="The AutoML Job key",direction=API.Direction.OUTPUT)                     public Job job;
}
