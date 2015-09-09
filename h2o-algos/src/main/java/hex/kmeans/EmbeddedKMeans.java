package hex.kmeans;

import water.Job;
import water.Key;

public class EmbeddedKMeans extends KMeans {
  final private Key sharedProgressKey;
  final private Key superJobKey;

  public EmbeddedKMeans(Key glrmJobKey, Key sharedProgressKey, KMeansModel.KMeansParameters parms) {
    super(parms);
    this.sharedProgressKey = sharedProgressKey;
    this.superJobKey = glrmJobKey;
  }

  @Override
  protected Key createProgressKey() {
    return sharedProgressKey != null ? sharedProgressKey : super.createProgressKey();
  }

  @Override
  protected boolean deleteProgressKey() {
    return false;
  }

  @Override
  public boolean isRunning() {
    return super.isRunning() && ((Job) superJobKey.get()).isRunning();
  }
}
