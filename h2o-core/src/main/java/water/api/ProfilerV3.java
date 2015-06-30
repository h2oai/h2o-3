package water.api;

import water.Iced;

public class ProfilerV3 extends RequestSchema<Iced, ProfilerV3> {
  @API(help="Stack trace depth", required=true)
  public int depth = 10;

  @API(help="", direction = API.Direction.OUTPUT)
  public ProfilerNodeV3[] nodes;
}
