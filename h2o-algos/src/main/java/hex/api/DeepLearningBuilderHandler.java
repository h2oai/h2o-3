package hex.api;

import hex.deeplearning.DeepLearning;
import hex.schemas.DeepLearningV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class DeepLearningBuilderHandler extends ModelBuilderHandler<DeepLearning, DeepLearningV2, DeepLearningV2.DeepLearningParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, DeepLearningV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DeepLearningV2 validate_parameters(int version, DeepLearningV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

