package hex.pipeline;

import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

public class PipelineAlgoRegistration extends AlgoAbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    Pipeline builder = new Pipeline(true);
    registerModelBuilder(context, builder, SchemaServer.getStableVersion());
  }

}
