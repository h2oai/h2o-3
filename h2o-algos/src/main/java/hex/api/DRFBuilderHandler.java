package hex.api;

import hex.schemas.DRFV3;
import hex.tree.drf.DRF;
import water.api.ModelBuilderHandler;

public class DRFBuilderHandler extends ModelBuilderHandler<DRF, DRFV3, DRFV3.DRFParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DRFV3 train(int version, DRFV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DRFV3 validate_parameters(int version, DRFV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

