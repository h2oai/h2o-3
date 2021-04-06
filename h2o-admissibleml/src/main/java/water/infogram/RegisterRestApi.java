package water.infogram;

import hex.Infogram.Infogram;
import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

public class RegisterRestApi extends AlgoAbstractRegister {
  @Override
  public void registerEndPoints(RestApiContext context) {
    Infogram infogramMB = new Infogram(true);
    // Register InfoGram model builder REST API
    registerModelBuilder(context, infogramMB, SchemaServer.getStableVersion());
  }

  @Override
  public String getName() {
    return "Infogram";
  }
}
