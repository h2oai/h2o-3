package water.api;

import java.util.ServiceLoader;

public abstract class AbstractRegister implements RestApiExtension {

  @Override
  public void registerEndPoints(RestApiContext context) {
  }

  @Override
  public void registerSchemas(RestApiContext context) {
    assert context != null : "Context needs to be passed!";
    ServiceLoader<Schema> schemaLoader = ServiceLoader.load(Schema.class);
    for (Schema schema : schemaLoader) {
      context.registerSchema(schema);
    }
  }

  @Override
  public String getName() {
    return this.getClass().getName();
  }
}
