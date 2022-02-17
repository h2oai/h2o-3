package hex.api.example;

import hex.example.Example;
import hex.example.ExampleExtension;
import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

import java.util.Collections;
import java.util.List;

public class RegisterRestApi extends AlgoAbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    Example exampleMB = new Example(true);
    // Register Example model builder REST API
    registerModelBuilder(context, exampleMB, SchemaServer.getStableVersion());
  }

  @Override
  public String getName() {
    return "Example";
  }

  @Override
  public List<String> getRequiredCoreExtensions() {
    return Collections.singletonList(ExampleExtension.NAME);
  }

}
