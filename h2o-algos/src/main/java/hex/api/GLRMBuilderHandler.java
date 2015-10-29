package hex.api;

import hex.glrm.GLRM;
import hex.schemas.GLRMV3;
import water.api.ModelBuilderHandler;

public class GLRMBuilderHandler extends ModelBuilderHandler<GLRM, GLRMV3, GLRMV3.GLRMParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLRMV3 train(int version, GLRMV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLRMV3 validate_parameters(int version, GLRMV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
