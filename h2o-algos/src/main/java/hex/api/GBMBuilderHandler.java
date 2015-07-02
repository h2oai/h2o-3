package hex.api;

import hex.schemas.GBMV3;
import hex.tree.gbm.GBM;
import water.api.ModelBuilderHandler;

public class GBMBuilderHandler extends ModelBuilderHandler<GBM, GBMV3, GBMV3.GBMParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GBMV3 train(int version, GBMV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GBMV3 validate_parameters(int version, GBMV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

