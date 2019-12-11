package water.api;

import water.Paxos;
import water.api.schemas3.CloudLockV3;

class CloudLockHandler extends Handler {
  
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudLockV3 lock(int version, CloudLockV3 cloud) {
    Paxos.lockCloud("requested via REST api");
    return cloud;
  }
}
