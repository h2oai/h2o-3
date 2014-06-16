package hex.schemas;

import hex.kmeans.KMeans;
import water.Key;
import water.H2O;
import water.api.Handler;

public class KMeansHandler extends Handler<KMeansHandler,KMeansV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // Inputs
  public Key _src;              // Frame being clustered
  public int _K;                // Number of clusters
  public boolean _normalize;    // Normalize columns
  public int _max_iters;        // Max iterations
  public long _seed;            // RNG seed
  public KMeans.Initialization _init;

  // Outputs
  public KMeans _job;           // The modelling job

  public KMeansHandler() {}
  public void work() { _job = new KMeans(this); }
  @Override protected KMeansV2 schema(int version) { return new KMeansV2(); }
  @Override public void compute2() { throw H2O.fail(); }
}
