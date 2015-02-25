package hex.api;

import hex.pca.PCA;
import hex.schemas.PCAV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class PCABuilderHandler extends ModelBuilderHandler<PCA, PCAV2, PCAV2.PCAParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, PCAV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public PCAV2 validate_parameters(int version, PCAV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}