package hex.api;

import hex.grep.Grep;
import hex.schemas.GrepV3;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class GrepBuilderHandler extends ModelBuilderHandler<Grep, GrepV3, GrepV3.GrepParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, GrepV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GrepV3 validate_parameters(int version, GrepV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

