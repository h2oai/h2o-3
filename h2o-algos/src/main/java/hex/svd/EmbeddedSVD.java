package hex.svd;

import water.Job;
import water.Key;

public class EmbeddedSVD extends SVD {
  final private Key sharedProgressKey;
  final private Key superJobKey;

  public EmbeddedSVD(Key pcaJobKey, Key sharedProgressKey, SVDModel.SVDParameters parms) {
    super(parms);
    this.sharedProgressKey = sharedProgressKey;
    this.superJobKey = pcaJobKey;
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