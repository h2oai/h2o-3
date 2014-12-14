package hex.api;

import hex.kmeans.KMeans;
import hex.schemas.KMeansV2;
import water.api.ModelBuilderHandler;
import water.api.Schema;

public class KMeansBuilderHandler extends ModelBuilderHandler<KMeans, KMeansV2, KMeansV2.KMeansParametersV2> {
  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public Schema train(int version, KMeansV2 builderSchema) {
    return super.do_train(version, builderSchema);
  }

  /** Required so that Handler.handle() gets the correct schema types. */
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public KMeansV2 validate_parameters(int version, KMeansV2 builderSchema) {
    return super.do_validate_parameters(version, builderSchema);
  }
}

