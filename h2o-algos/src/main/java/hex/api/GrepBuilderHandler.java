package hex.api;

import hex.grep.Grep;
import hex.schemas.GrepV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class GrepBuilderHandler extends ModelBuilderHandler<Grep, GrepV2, GrepV2.GrepParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, GrepV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public GrepV2 validate_parameters(int version, GrepV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

