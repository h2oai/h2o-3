package water.api;

import water.Paxos;
import water.api.schemas3.CloudLockV3;

class CloudLockHandler extends Handler {
  
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudLockV3 lock(int version, CloudLockV3 cloudLock) {
    StringBuilder builder = new StringBuilder("requested via REST api.");
    if (cloudLock.reason != null) {
      builder.append(" Reason: ").append(cloudLock.reason);
    }
    Paxos.lockCloud(builder.toString());
    return cloudLock;
  }
}
