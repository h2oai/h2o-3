package water.automl;

import ai.h2o.automl.AutoML;
import water.Iced;
import water.api.API;
import water.api.KeyV3;
import water.api.RequestSchema;

public class AutoMLV3 extends RequestSchema<Iced,AutoMLV3> {
  @API(help="The AutoML key",direction=API.Direction.INPUT)           public AutoML.AutoMLKeyV3 automl_id;
  @API(help="the leader model's key", direction=API.Direction.OUTPUT) public KeyV3.ModelKeyV3   leader;
}
