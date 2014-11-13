package hex.schemas;

import hex.kmeans2.KMeans2;
import water.H2O;
import water.api.Handler;

/** TODO: only used by old-school web ui: remove!  ModelBuilderHander does this for all the algos.  */
@Deprecated
public class KMeans2Handler extends Handler<KMeans2,KMeans2V2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public KMeans2Handler() {}
  public KMeans2V2 train(int version, KMeans2 e) {
    assert e._parms != null;
    e.trainModel();
    return schema(version).fillFromImpl(e);
  }
  @Override protected KMeans2V2 schema(int version) { KMeans2V2 schema = new KMeans2V2(); schema.parameters = schema.createParametersSchema(); return schema; }
  @Override public void compute2() { throw H2O.fail(); }
}
