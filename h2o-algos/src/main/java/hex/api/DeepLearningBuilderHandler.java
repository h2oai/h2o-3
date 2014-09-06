package hex.api;

import hex.deeplearning.DeepLearning;
import hex.schemas.DeepLearningV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class DeepLearningBuilderHandler extends ModelBuilderHandler<DeepLearning, DeepLearningV2, DeepLearningV2.DeepLearningParametersV2> {

  @Override protected DeepLearningV2 schema(int version) {
    switch (version) {
      case 2:   { DeepLearningV2 b = new DeepLearningV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

