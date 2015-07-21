package hex.api;

import hex.deeplearning.DeepLearning;
import hex.schemas.DeepLearningV3;
import water.api.ModelBuilderHandler;

public class DeepLearningBuilderHandler extends ModelBuilderHandler<DeepLearning, DeepLearningV3, DeepLearningV3.DeepLearningParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DeepLearningV3 train(int version, DeepLearningV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DeepLearningV3 validate_parameters(int version, DeepLearningV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

