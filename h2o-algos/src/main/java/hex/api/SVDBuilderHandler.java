package hex.api;

import hex.schemas.SVDV99;
import hex.svd.SVD;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class SVDBuilderHandler extends ModelBuilderHandler<SVD, SVDV99, SVDV99.SVDParametersV99> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, SVDV99 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public SVDV99 validate_parameters(int version, SVDV99 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
