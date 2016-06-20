package water.api.schemas3;

import water.Iced;
import water.api.API;

public class InitIDV3 extends SchemaV3<Iced, InitIDV3> {
  @API(help="Session ID", direction = API.Direction.OUTPUT)
  public String session_key;
}
