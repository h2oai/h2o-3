package hex.api;

import hex.kmeans.KMeans;
import hex.schemas.KMeansV3;
import water.api.ModelBuilderHandler;

public class KMeansBuilderHandler extends ModelBuilderHandler<KMeans, KMeansV3, KMeansV3.KMeansParametersV3> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public KMeansV3 train(int version, KMeansV3 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public KMeansV3 validate_parameters(int version, KMeansV3 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

