package hex.api;

import hex.coxph.CoxPH;
import hex.schemas.CoxPHV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class CoxPHBuilderHandler extends ModelBuilderHandler<CoxPH, CoxPHV2, CoxPHV2.CoxPHParametersV2> {

  @Override protected CoxPHV2 schema(int version) {
    switch (version) {
      case 2:   { CoxPHV2 b = new CoxPHV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

