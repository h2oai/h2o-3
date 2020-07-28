package hex.api.targetencoding;

import ai.h2o.targetencoding.TargetEncoderBuilder;
import ai.h2o.targetencoding.TargetEncoderModel;
import hex.ModelBuilder;
import water.api.AlgoAbstractRegister;
import water.api.RestApiContext;
import water.api.SchemaServer;

public class TargetEncoderAlgoRegister extends AlgoAbstractRegister {

  @Override
  public void registerEndPoints(RestApiContext context) {
    ModelBuilder targetEncoderModelBuilder = new TargetEncoderBuilder(true);
    registerModelBuilder(context, targetEncoderModelBuilder, SchemaServer.getStableVersion());

    context.registerEndpoint("target_encoder_transform", "GET /3/TargetEncoderTransform", TargetEncoderHandler.class, "transform",
            "Transform using give TargetEncoderModel");
  }

  @Override
  public String getName() {
    return TargetEncoderModel.ALGO_NAME;
  }
  
}
