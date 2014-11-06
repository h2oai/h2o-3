package water.api;

import water.H2O;
import water.H2ONode;
import water.Iced;
import water.api.CloudHandler.Cloud;
import water.Paxos;

class CloudHandler extends Handler<Cloud,CloudV1> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  // TODO: this really ought to be in the water package
  protected static final class Cloud extends Iced {
    String _version, _cloud_name;
    long _uptime_ms;
    boolean _consensus, _locked;
    H2ONode[] _members;
  }

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public CloudV1 status(int version, Cloud cloud) {
    // TODO: this really ought to be in the water package
    cloud._version = H2O.ABV.projectVersion();
    cloud._cloud_name = H2O.ARGS.name;
    cloud._uptime_ms = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    cloud._consensus = Paxos._commonKnowledge;
    cloud._locked = Paxos._cloudLocked;
    cloud._members = H2O.CLOUD.members();
    return schema(version).fillFromImpl(cloud);
  }

  // Cloud Schemas are still at V1, unchanged for V2
  @Override protected CloudV1 schema(int version) { return new CloudV1(); }
}

