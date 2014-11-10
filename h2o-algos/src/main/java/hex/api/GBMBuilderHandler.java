package hex.api;

import hex.tree.gbm.GBM;
import hex.schemas.GBMV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class GBMBuilderHandler extends ModelBuilderHandler<GBM, GBMV2, GBMV2.GBMParametersV2> {

  @Override protected GBMV2 schema(int version) {
    switch (version) {
      case 2:   { GBMV2 b = new GBMV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

