package water.schemas;

import water.api.Cloud;
import water.H2O;
import water.H2ONode;

public class CloudV1 extends Schema<Cloud,CloudV1> {

  // Input fields
  private final Inputs _ins = new Inputs();
  private static class Inputs {
    // This Schema has no inputs
  }

  // Output fields
  private final Outputs _outs = new Outputs();
  private static class Outputs {
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
    private static class Node {
      @API(help="sys_load")
      public float sys_load;
    }

  }

  //==========================
  // Customer adapters for V1 Go Here

  // TODO: This is a custom adapter that should be handled automatically by the
  // parent Schema class
  public static CloudV1 makeSchema(Cloud C) {
    CloudV1 cv1 = new CloudV1();
    cv1._outs.version = C._version;
    cv1._outs.cloud_name = C._cloud_name;
    cv1._outs.cloud_size = C._cloud_size;
    cv1._outs.cloud_uptime_millis = C._uptime_ms;
    cv1._outs.cloud_healthy = C._cloud_healthy;
    cv1._outs.consensus = C._consensus;
    cv1._outs.locked = C._locked;
    H2ONode[] members = C._members;
    // Slice out the subset of H2ONode info into the nodes for display
    cv1._outs.nodes = new Outputs.Node[members.length];
    for( int i=0; i<members.length; i++ )
      cv1._outs.nodes[i].sys_load = members[i]._heartbeat._system_load_average;
    return cv1;
  }

  // This is a custom adapter that fails, since you cannot import a Cloud
  // status page, nor set the status merely by passing in a URL
  public Cloud makeIced(CloudV1 schema) {
    throw H2O.fail();
  }

}
