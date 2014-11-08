package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel.KMeansParameters;
import water.H2O;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class KMeansHandler extends Handler<KMeans, KMeansV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public KMeansHandler() {}

  // TODO: move this into a new ModelBuilderHandler superclass
  // TODO: also add a score method in the new ModelBuilderHandler superclass
  public KMeansV2 train(int version, KMeans builder) {
    KMeansParameters parms = builder._parms;
    assert parms != null; /* impl._job = */
    builder.trainModel();
    KMeansV2 schema = schema(version).fillFromImpl(builder); // TODO: superclass!
    schema.job = builder._key;
    return schema;
  }
  @Override protected KMeansV2 schema(int version) { KMeansV2 schema = new KMeansV2(); schema.parameters = schema.createParametersSchema(); return schema; }
  @Override public void compute2() { throw H2O.fail(); }
}
