package water.api;

import java.util.Collections;
import java.util.List;
import java.util.ServiceLoader;

public abstract class AbstractRegister implements RestApiExtension {

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

  @Override
  public List<String> getRequiredCoreExtensions() {
    return Collections.emptyList();
  }

}
