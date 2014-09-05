package hex.api;

import hex.kmeans.KMeans;
import hex.schemas.KMeansV2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class KMeansBuilderHandler extends ModelBuilderHandler<KMeans, KMeansV2, KMeansV2.KMeansParametersV2> {

  @Override protected KMeansV2 schema(int version) {
    switch (version) {
      case 2:   { KMeansV2 b = new KMeansV2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

