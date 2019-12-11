package water.api;

import water.Paxos;
import water.api.schemas3.CloudV3;

class CloudLockHandler extends Handler {
  
  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudV3 lock(int version, CloudV3 cloud) {
    Paxos.lockCloud("requested via REST api");
    return cloud;
  }
}
