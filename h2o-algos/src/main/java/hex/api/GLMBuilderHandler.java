package hex.api;

import hex.glm.GLM;
import hex.schemas.GLMV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class GLMBuilderHandler extends ModelBuilderHandler<GLM, GLMV2, GLMV2.GLMParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, GLMV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLMV2 validate_parameters(int version, GLMV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

