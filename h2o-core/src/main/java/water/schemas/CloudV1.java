package water.schemas;

import water.H2O;
import water.H2ONode;
import water.Iced;
import water.api.Cloud;
import water.api.Handler;

public class CloudV1 extends Schema<Cloud,CloudV1> {
  // This Schema has no inputs

  // Output fields
  @API(help="version")
  public String version;
  
  @API(help="cloud_name")
  public String cloud_name;
  
  @API(help="cloud_size")
  public int cloud_size;

  @API(help="cloud_uptime_millis")
  public long cloud_uptime_millis;

  @API(help="cloud_healthy")
  public boolean cloud_healthy;

  @API(help="consensus")
  public boolean consensus;

  @API(help="locked")
  public boolean locked;

  @API(help="nodes")
  public Node[] nodes;
  
  // Output fields one-per-JVM
  private static class Node extends Iced {
    @API(help="sys_load")
    final boolean healthy;
    final float sys_load;
    Node( float sys_load, boolean healthy ) {
      this.healthy = healthy;
      this.sys_load = sys_load;
    }
  }

  //==========================
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public CloudV1 fillInto( Cloud h ) {
    return this;                // No inputs
  }

  // Version&Schema-specific filling from the handler
  @Override public CloudV1 fillFrom( Cloud h ) {
    version = h._version;
    cloud_name = h._cloud_name;
    cloud_size = h._members.length;
    cloud_uptime_millis = h._uptime_ms;
    cloud_healthy = h._cloud_healthy;
    consensus = h._consensus;
    locked = h._locked;
    nodes = new Node[h._members.length];
    for( int i=0; i<h._members.length; i++ )
      nodes[i] = new Node(h._members[i]._heartbeat._system_load_average,
                          h._members[i]._node_healthy
                          );
    return this;
  }

}
