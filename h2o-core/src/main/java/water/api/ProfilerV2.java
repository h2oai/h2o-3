package water.api;

import water.Iced;

public class ProfilerV2 extends Schema<Iced, ProfilerV2> {
  @API(help="Stack trace depth", required=true)
  public int depth = 10;

  @API(help="", direction = API.Direction.OUTPUT)
  public ProfilerNodeV2[] nodes;
}
