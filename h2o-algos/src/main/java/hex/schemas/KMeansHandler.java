package hex.schemas;

import water.*;
import water.api.Handler;

public class KMeansHandler extends Handler<KMeansHandler,KMeansV2> {
  @Override protected int min_ver() { return 2; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }
  // Inputs
  Key _src; // Key holding final value after job is removed

  int _K;                       // Number of clusters

  // Output

  // The model!
  Key _job;

  public KMeansHandler() {}

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { 
    throw H2O.unimpl();
  }

  @Override protected KMeansV2 schema(int version) { return new KMeansV2(); }
}
