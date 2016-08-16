package water.automl;


import water.Iced;
import water.api.API;
import water.api.RequestSchema;

public class AutoMLJSONSChemaV3 extends RequestSchema<Iced,AutoMLJSONSChemaV3> {
  @API(help="json schema", direction=API.Direction.OUTPUT) public String json_schema;
}
