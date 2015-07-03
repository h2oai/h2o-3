package hex.api;

import hex.pca.PCA;
import hex.schemas.PCAV99;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class PCABuilderHandler extends ModelBuilderHandler<PCA, PCAV99, PCAV99.PCAParametersV99> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, PCAV99 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public PCAV99 validate_parameters(int version, PCAV99 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}
