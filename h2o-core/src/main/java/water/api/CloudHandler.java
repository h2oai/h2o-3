package water.api;

import water.*;
import water.api.CloudHandler.Cloud;

class CloudHandler extends Handler<Cloud,CloudV1> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // TODO: this really ought to be in the water package
  protected static final class Cloud extends Iced {
    String _version;
    String _cloud_name;
    int _cloud_size;
    long _cloud_uptime_millis;
    boolean _cloud_healthy;
    int _bad_nodes;
    boolean _consensus;
    boolean _locked;
    H2ONode[] _members;
  }

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudV1 status(int version, Cloud cloud) {
    // TODO: this really ought to be in the water package
    cloud._version = H2O.ABV.projectVersion();
    cloud._cloud_name = H2O.ARGS.name;
    cloud._cloud_size = H2O.CLOUD.size();
    cloud._cloud_uptime_millis = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    cloud._consensus = Paxos._commonKnowledge;
    cloud._locked = Paxos._cloudLocked;
    cloud._members = H2O.CLOUD.members();

    // Calculate cloud metrics from individual node metrics.
    cloud._cloud_healthy = true;
    cloud._bad_nodes = 0;
    for (H2ONode node : H2O.CLOUD.members()) {
      Long elapsed = System.currentTimeMillis() - node._last_heard_from;
      boolean node_healthy = elapsed > HeartBeatThread.TIMEOUT ? false : true;
      if (! node_healthy) {
        cloud._cloud_healthy = false;
        cloud._bad_nodes++;
      }
    }

    return schema(version).fillFromImpl(cloud);
  }

  // Cloud Schemas are still at V1, unchanged for V2
  @Override protected CloudV1 schema(int version) { return new CloudV1(); }
}

