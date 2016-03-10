package ai.h2o.automl.schemas;

import water.api.API;
import water.api.KeyV3;
import water.api.Schema;

public class AutoMLV3 extends Schema {
  @API(help="the name of the dataset",direction=API.Direction.INPUT)                 public String dataset;
  @API(help="response column",direction=API.Direction.INPUT)                         public int response;
  @API(help="loss function",direction=API.Direction.INPUT)                           public String loss;
  @API(help="maximum run time in seconds",direction=API.Direction.INPUT)             public long maxTime;
  @API(help="Allow AutoML to build ensembles",direction=API.Direction.INPUT)         public boolean ensemble;
  @API(help="Prevent AutoML from trying these models",direction=API.Direction.INPUT) public String[] exclude;
  @API(help="Try frame transformations",direction=API.Direction.INPUT)               public boolean tryMutations;
  @API(help="The AutoML result",direction=API.Direction.OUTPUT)                      public KeyV3.ModelKeyV3 result;
}
