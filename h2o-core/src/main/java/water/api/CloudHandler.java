package water.api;

import water.H2O;
import water.H2ONode;
import water.Paxos;

class CloudHandler extends Handler {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudV1 status(int version, CloudV1 cloud) {
    // TODO: this really ought to be in the water package
    cloud.version = H2O.ABV.projectVersion();
    cloud.cloud_name = H2O.ARGS.name;
    cloud.cloud_size = H2O.CLOUD.size();
    cloud.cloud_uptime_millis = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    cloud.consensus = Paxos._commonKnowledge;
    cloud.locked = Paxos._cloudLocked;

    // Fetch and calculate cloud metrics from individual node metrics.
    H2ONode[] members = H2O.CLOUD.members();
    cloud.bad_nodes = 0;
    cloud.cloud_healthy = true;
    if (null != members) {
        cloud.nodes = new CloudV1.Node[members.length];
        for (int i = 0; i < members.length; i++) {
          cloud.nodes[i] = new CloudV1.Node(members[i]);
          if (! cloud.nodes[i].healthy) {
            cloud.cloud_healthy = false;
            cloud.bad_nodes++;
          }
        }
      }

    return cloud;
  }
}

