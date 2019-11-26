package water.api.schemas3;

import water.Iced;
import water.api.API;

public class PingV3 extends RequestSchemaV3<Iced, PingV3> {
  public PingV3() {
  }

  @API(help = "cloud_uptime_millis", direction = API.Direction.OUTPUT)
  public long cloud_uptime_millis;

  @API(help = "cloud_healthy", direction = API.Direction.OUTPUT)
  public boolean cloud_healthy;

  @API(help="nodes", direction=API.Direction.OUTPUT)
  public NodeMemoryInfoV3[] nodes;
  
  public static class NodeMemoryInfoV3 extends SchemaV3<Iced, NodeMemoryInfoV3> {
    public NodeMemoryInfoV3() {
    }

    public NodeMemoryInfoV3(String ipPort, long freeMemory) {
      ip_port = ipPort;
      free_mem = freeMemory;
    }
    
    @API(help="IP address and port in the form a.b.c.d:e", direction=API.Direction.OUTPUT)
    public String ip_port;

    @API(help = "Free heap", direction = API.Direction.OUTPUT)
    public long free_mem;
  }
}
