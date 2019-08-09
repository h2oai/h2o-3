package ai.h2o.automl.targetencoding;

import hex.ModelBuilder;
import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

public class TargetEncoderModelAlgoRegister extends AlgoAbstractRegister {
  
  @Override
  public void registerEndPoints(RestApiContext context) {
    ModelBuilder targetEncoderModelBuilder = new TargetEncoderBuilder(true);
    registerModelBuilder(context, targetEncoderModelBuilder, SchemaServer.getStableVersion());
  }

  @Override
  public String getName() {
    return TargetEncoderModel.ALGO_NAME;
  }
}
