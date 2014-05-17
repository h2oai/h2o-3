package water.api;

import water.H2O.H2OCountedCompleter;
import water.H2O;
import water.H2ONode;
import water.Paxos;
import water.schemas.CloudV1;

public class Cloud extends H2OCountedCompleter {

  public String _version, _cloud_name;
  public int _cloud_size;
  public long _uptime_ms;
  public boolean _cloud_healthy, _consensus, _locked;
  public H2ONode[] _members;

  @Override public void compute2() {
    _version = H2O.ABV.projectVersion();
    _cloud_name = H2O.ARGS.name;
    _uptime_ms = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    _consensus = Paxos._commonKnowledge;
    _locked = Paxos._cloudLocked;
    H2O cloud = H2O.CLOUD;
    _members = cloud.members();
    _cloud_size = _members.length;
    _cloud_healthy = true;
    for( H2ONode h2o : _members ) _cloud_healthy &= h2o._node_healthy;
    tryComplete();
  }
}

