package water.automl.api.schemas3;


import water.Iced;
import water.api.API;
import water.api.schemas3.SchemaV3;

// TODO: this is about to change from SchemaV3 to RequestSchemaV3:
public class AutoMLJSONSchemaV3 extends SchemaV3<Iced,AutoMLJSONSchemaV3> {
  @API(help="json schema", direction=API.Direction.OUTPUT) public String json_schema;
}
