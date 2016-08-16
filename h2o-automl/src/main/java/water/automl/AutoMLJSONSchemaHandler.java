package water.automl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.module.jsonSchema.factories.SchemaFactoryWrapper;
import com.fasterxml.jackson.module.jsonSchema.types.ObjectSchema;
import water.api.Handler;


public class AutoMLJSONSchemaHandler extends Handler {
  public AutoMLJSONSchemaV3 getJSONSchema(int version, AutoMLJSONSchemaV3 args) {

    Class clazz = AutoMLBuilderV3.class;
    ObjectMapper m = new ObjectMapper();

    SchemaFactoryWrapper visitor = new SchemaFactoryWrapper();
    try {
      m.acceptJsonFormatVisitor(m.constructType(clazz), visitor);
    } catch (JsonMappingException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    com.fasterxml.jackson.module.jsonSchema.JsonSchema jsonSchema = visitor.finalSchema();
    ((ObjectSchema) jsonSchema).getProperties().remove("job");  // heh, get at and mutate the private field :)
    ((ObjectSchema) jsonSchema).getProperties().remove("implClass");
    ((ObjectSchema) jsonSchema).getProperties().remove("_exclude_fields");
    ((ObjectSchema) jsonSchema).getProperties().remove("schemaVersion");

    try {
      args.json_schema = m.writerWithDefaultPrettyPrinter().writeValueAsString(jsonSchema);
    } catch (JsonProcessingException e) {
      e.printStackTrace();
      throw new RuntimeException(e);
    }
    return args;
  }
}