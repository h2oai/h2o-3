package hex.api;

import hex.schemas.SVDV3;
import hex.svd.SVD;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class SVDBuilderHandler extends ModelBuilderHandler<SVD, SVDV3, SVDV3.SVDParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, SVDV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public SVDV3 validate_parameters(int version, SVDV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
