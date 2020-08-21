package water.api;

import water.H2O;
import water.H2ONode;
import water.api.schemas3.PingV3;

public class PingHandler extends Handler {
  
  // Time in msec since somebody accessed the '3/Ping' endpoint on this node
  public static long lastAccessed; 

  @SuppressWarnings("unused") // called through reflection by RequestServer
  public PingV3 ping(int version, PingV3 ping) {

    ping.cloud_uptime_millis = System.currentTimeMillis() - H2O.START_TIME_MILLIS.get();
    ping.cloud_healthy = true;
    H2ONode[] members = H2O.CLOUD.members();

    if (null != members) {
      ping.nodes = new PingV3.NodeMemoryInfoV3[members.length];
      for (int i = 0; i < members.length; i++) {
        H2ONode n = members[i];
        ping.nodes[i] = new PingV3.NodeMemoryInfoV3(n.getIpPortString(), n._heartbeat.get_free_mem());
      }
    }
    lastAccessed = System.currentTimeMillis();
    return ping;
  }
}
