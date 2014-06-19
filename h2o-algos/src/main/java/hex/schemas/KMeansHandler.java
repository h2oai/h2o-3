package hex.schemas;

import hex.kmeans.KMeans;
import hex.kmeans.KMeansModel;
import water.H2O;
import water.api.Handler;

public class KMeansHandler extends Handler<KMeansHandler,KMeansV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // Inputs
  public KMeansModel.KMeansParameters _parms;

  // Outputs
  public KMeans _job;           // The modelling job

  public KMeansHandler() {}
  public void work() { assert _parms != null; _job = new KMeans(_parms); }
  @Override protected KMeansV2 schema(int version) { return new KMeansV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
