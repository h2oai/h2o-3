package hex.api;

import hex.example.Example;
import hex.schemas.ExampleV3;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class ExampleBuilderHandler extends ModelBuilderHandler<Example, ExampleV3, ExampleV3.ExampleParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, ExampleV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public ExampleV3 validate_parameters(int version, ExampleV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

