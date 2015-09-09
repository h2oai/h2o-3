package hex.glrm;

import water.Job;
import water.Key;

public class EmbeddedGLRM extends GLRM {
  final private Key sharedProgressKey;
  final private Key superJobKey;

  public EmbeddedGLRM(Key glrmJobKey, Key sharedProgressKey, GLRMModel.GLRMParameters parms) {
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