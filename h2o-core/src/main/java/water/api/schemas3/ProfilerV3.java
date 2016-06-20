package water.api.schemas3;

import water.Iced;
import water.api.API;

public class ProfilerV3 extends SchemaV3<Iced, ProfilerV3> {
  @API(help="Stack trace depth", required=true)
  public int depth = 10;

  @API(help="", direction = API.Direction.OUTPUT)
  public ProfilerNodeV3[] nodes;
}
