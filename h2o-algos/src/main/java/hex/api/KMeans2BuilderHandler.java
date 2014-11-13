package hex.api;

import hex.kmeans2.KMeans2;
import hex.schemas.KMeans2V2;
import water.H2O;
import water.api.ModelBuilderHandler;

public class KMeans2BuilderHandler extends ModelBuilderHandler<KMeans2, KMeans2V2, KMeans2V2.KMeans2ParametersV2> {

  @Override protected KMeans2V2 schema(int version) {
    switch (version) {
      case 2:   { KMeans2V2 b = new KMeans2V2(); b.parameters = b.createParametersSchema(); return b; }
      default:  throw H2O.fail("Bad version for ModelBuilder schema: " + version);
    }
  }
}

