package hex.api;

import hex.glrm.GLRM;
import hex.schemas.GLRMV99;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class GLRMBuilderHandler extends ModelBuilderHandler<GLRM, GLRMV99, GLRMV99.GLRMParametersV99> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, GLRMV99 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GLRMV99 validate_parameters(int version, GLRMV99 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
