package hex.api;

import hex.grep.Grep;
import hex.schemas.GrepV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class GrepBuilderHandler extends ModelBuilderHandler<Grep, GrepV2, GrepV2.GrepParametersV2> {

  @Override protected GrepV2 schema(int version) {
    switch (version) {
      case 2:   { GrepV2 b = new GrepV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

