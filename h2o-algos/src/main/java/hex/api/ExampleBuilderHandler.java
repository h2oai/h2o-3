package hex.api;

import hex.example.Example;
import hex.schemas.ExampleV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class ExampleBuilderHandler extends ModelBuilderHandler<Example, ExampleV2, ExampleV2.ExampleParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, ExampleV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ExampleV2 validate_parameters(int version, ExampleV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

