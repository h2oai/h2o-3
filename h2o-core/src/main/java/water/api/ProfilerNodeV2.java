package water.api;

import water.Iced;

public class ProfilerNodeV2 extends Schema<Iced, ProfilerNodeV2> {
  public static class ProfilerNodeEntryV2 extends Schema<Iced, ProfilerNodeEntryV2> {
    @API(help="Stack trace", direction=API.Direction.OUTPUT)
    public String stacktrace;

    @API(help="Profile Count", direction=API.Direction.OUTPUT)
    public int count;
  }

  @API(help="Node names", direction=API.Direction.OUTPUT)
  public String node_name;

  @API(help="Timestamp (millis since epoch)", direction=API.Direction.OUTPUT)
  public long timestamp;

  @API(help="Profile entry list", direction=API.Direction.OUTPUT)
  public ProfilerNodeEntryV2[] entries;
}
