package hex.api;

import hex.schemas.DRFV2;
import static hex.schemas.DRFV2.*;
import hex.tree.drf.DRF;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class DRFBuilderHandler extends ModelBuilderHandler<DRF, DRFV2, DRFV2.DRFParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, DRFV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public DRFV2 validate_parameters(int version, DRFV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

