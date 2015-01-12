package water.api;

import water.Iced;

public class InitIDV1 extends Schema<Iced, InitIDV1> {
  @API(help="Session ID", direction = API.Direction.OUTPUT)
  String session_key;
}
