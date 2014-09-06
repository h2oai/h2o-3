package hex.api;

import hex.example.Example;
import hex.schemas.ExampleV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class ExampleBuilderHandler extends ModelBuilderHandler<Example, ExampleV2, ExampleV2.ExampleParametersV2> {

  @Override protected ExampleV2 schema(int version) {
    switch (version) {
      case 2:   { ExampleV2 b = new ExampleV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

