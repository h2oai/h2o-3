package water.api;

import java.util.Properties;
import water.H2O.H2OCountedCompleter;
import water.H2O;
import water.H2ONode;
import water.Paxos;
import water.schemas.CloudV1;
import water.schemas.Schema;

public class Cloud extends Handler {

  public String _version, _cloud_name;
  public int _cloud_size;
  public long _uptime_ms;
  public boolean _cloud_healthy, _consensus, _locked;
  public H2ONode[] _members;

  // Running all in exec2, no need for backgrounding on F/J threads
  @Override public void compute2() { throw H2O.fail(); }

  protected void status() {
    //_version = H2O.ABV.projectVersion();
    //_cloud_name = H2O.ARGS.name;
    //_uptime_ms = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    //_consensus = Paxos._commonKnowledge;
    //_locked = Paxos._cloudLocked;
    //H2O cloud = H2O.CLOUD;
    //_members = cloud.members();
    //_cloud_size = _members.length;
    //_cloud_healthy = true;
    //for( H2ONode h2o : _members ) _cloud_healthy &= h2o._node_healthy;
    throw H2O.unimpl();
  }

  // Supported at V1 same as always
  @Override protected int min_ver() { return 1; }
  // Cloud Schemas are still at V1, unchanged for V2
  @Override protected CloudV1 schema(int version) { return new CloudV1(); }
}

