package water.api.schemas3;

import water.Iced;
import water.api.API;

public class InitIDV3 extends RequestSchemaV3<Iced, InitIDV3> {
  @API(help="Session ID", direction = API.Direction.INOUT)
  public String session_key;

  @API(help="Indicates whether users are allowed to set and modify session properties", direction = API.Direction.OUTPUT)
  public boolean session_properties_allowed;
}
