package water.automl;

import ai.h2o.automl.AutoML;
import hex.Model;
import water.Iced;
import water.Key;
import water.api.API;
import water.api.RequestSchema;

public class AutoMLV3 extends RequestSchema<Iced,AutoMLV3> {
  @API(help="The AutoML key",direction=API.Direction.INPUT)           public Key<AutoML> key;
  @API(help="the leader model's key", direction=API.Direction.OUTPUT) public Key<Model> leader;
}
