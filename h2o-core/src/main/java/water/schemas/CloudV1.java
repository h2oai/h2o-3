package water.schemas;

import water.Iced;

public class CloudV1 extends Schema<Iced,CloudV1> {

  // This Schema has no inputs
  private static class Inputs {
  }

  // Output fields
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

    //@API(help="nodes")
    //public NodeStatusSchemaV1[] nodes;
  }

  //==========================
  // Customer adapters for V1 Go Here
}
