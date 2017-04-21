package water.api.schemas3;

import water.api.API;

public class SteamMetricsV3 extends RequestSchemaV3<SteamMetricsV3.SteamMetrics, SteamMetricsV3> {
  public static class SteamMetrics extends water.Iced<SteamMetrics> {
    public SteamMetrics()
    {}
  }

  @API(help="Steam metrics API version", direction = API.Direction.OUTPUT)
  public long version;

  @API(help="Number of milliseconds that the cluster has been idle", direction = API.Direction.OUTPUT)
  public long idle_millis;
}
