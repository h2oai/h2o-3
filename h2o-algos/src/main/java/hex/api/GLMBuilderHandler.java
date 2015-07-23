package hex.api;

import hex.glm.GLM;
import hex.schemas.GLMV3;
import water.api.ModelBuilderHandler;

public class GLMBuilderHandler extends ModelBuilderHandler<GLM, GLMV3, GLMV3.GLMParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLMV3 train(int version, GLMV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLMV3 validate_parameters(int version, GLMV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

