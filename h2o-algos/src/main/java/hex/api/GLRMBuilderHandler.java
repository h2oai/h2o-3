package hex.api;

import hex.glrm.GLRM;
import hex.schemas.GLRMV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class GLRMBuilderHandler extends ModelBuilderHandler<GLRM, GLRMV2, GLRMV2.GLRMParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, GLRMV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLRMV2 validate_parameters(int version, GLRMV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}