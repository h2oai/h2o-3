package water.schemas;

import water.api.Cloud;
import water.api.Handler;
import water.H2O;
import water.H2ONode;

public class CloudV1 extends Schema {

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
  // Customer adapters Go Here

  // Version&Schema-specific filling into the handler
  @Override public CloudV1 fillInto( Handler h ) {
    throw H2O.unimpl();
  }

  // Version&Schema-specific filling from the handler
  @Override public CloudV1 fillFrom( Handler h ) {
    throw H2O.unimpl();
  }

}
