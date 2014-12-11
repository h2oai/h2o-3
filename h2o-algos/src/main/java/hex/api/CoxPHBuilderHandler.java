package hex.api;

import hex.coxph.CoxPH;
import hex.schemas.CoxPHV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class CoxPHBuilderHandler extends ModelBuilderHandler<CoxPH, CoxPHV2, CoxPHV2.CoxPHParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, CoxPHV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CoxPHV2 validate_parameters(int version, CoxPHV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

