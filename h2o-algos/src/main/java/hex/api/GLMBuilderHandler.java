package hex.api;

import hex.glm.GLM;
import hex.schemas.GLMV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class GLMBuilderHandler extends ModelBuilderHandler<GLM, GLMV2, GLMV2.GLMParametersV2> {

  @Override protected GLMV2 schema(int version) {
    switch (version) {
      case 2:   { GLMV2 b = new GLMV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

