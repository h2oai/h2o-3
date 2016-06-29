package water.api.schemas3;

import water.Iced;
import water.api.API;

public class ProfilerNodeV3 extends SchemaV3<Iced, ProfilerNodeV3> {

  public static class ProfilerNodeEntryV3 extends SchemaV3<Iced, ProfilerNodeEntryV3> {
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
  public ProfilerNodeEntryV3[] entries;
}
