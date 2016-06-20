package water.api;

import water.Iced;

public class InitIDV3 extends SchemaV3<Iced, InitIDV3> {
  @API(help="Session ID", direction = API.Direction.OUTPUT)
  String session_key;
}
