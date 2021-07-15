package water;

import water.api.AbstractRegister;
import water.api.InfoGramBuilderHandler;
import water.api.RestApiContext;

public class RegisterRestApi extends AbstractRegister {
  @Override
  public void registerEndPoints(RestApiContext context) {
    context.registerEndpoint("infogram_build",
            "POST /99/InfoGramBuilder", InfoGramBuilderHandler.class, "build",
            "Start an AutoML build process.");
  }
  
  @Override
  public String getName() {
    return "AdmissibleML";
  }
}
