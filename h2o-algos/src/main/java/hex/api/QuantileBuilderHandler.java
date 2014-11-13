package hex.api;

import hex.quantile.Quantile;
import hex.schemas.QuantileV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class QuantileBuilderHandler extends ModelBuilderHandler<Quantile, QuantileV2, QuantileV2.QuantileParametersV2> {

  @Override protected QuantileV2 schema(int version) {
    switch (version) {
      case 2:   { QuantileV2 b = new QuantileV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

