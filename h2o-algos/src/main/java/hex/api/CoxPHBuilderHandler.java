package hex.api;

import hex.coxph.CoxPH;
import hex.schemas.CoxPHV3;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class CoxPHBuilderHandler extends ModelBuilderHandler<CoxPH, CoxPHV3, CoxPHV3.CoxPHParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, CoxPHV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CoxPHV3 validate_parameters(int version, CoxPHV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

