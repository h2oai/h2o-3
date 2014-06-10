package water.api;

import java.util.Properties;
import water.H2O;
import water.H2ONode;
import water.Paxos;
import water.schemas.CloudV1;
import water.schemas.Schema;

public class CloudHandler extends Handler<CloudHandler,CloudV1> {
  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  @Override protected int max_ver() { return Integer.MAX_VALUE; }

  public String _version, _cloud_name;
  public long _uptime_ms;
  public boolean _consensus, _locked;
  public H2ONode[] _members;

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  protected void status() {
    _version = H2O.ABV.projectVersion();
    _cloud_name = H2O.ARGS.name;
    _uptime_ms = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    _consensus = Paxos._commonKnowledge;
    _locked = Paxos._cloudLocked;
    H2O cloud = H2O.CLOUD;
    _members = cloud.members();
  }

  // Cloud Schemas are still at V1, unchanged for V2
  @Override protected CloudV1 schema(int version) { return new CloudV1(); }
}

