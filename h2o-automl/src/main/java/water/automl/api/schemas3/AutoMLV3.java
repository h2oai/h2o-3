package water.automl.api.schemas3;

import ai.h2o.automl.AutoML;
import water.Iced;
import water.api.API;
import water.api.schemas3.KeyV3;
import water.api.schemas3.SchemaV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLV3 extends SchemaV3<Iced,AutoMLV3> {
  @API(help="The AutoML key",direction=API.Direction.INPUT)           public AutoML.AutoMLKeyV3 automl_id;
  @API(help="the leader model's key", direction=API.Direction.OUTPUT) public KeyV3.ModelKeyV3   leader;
}
