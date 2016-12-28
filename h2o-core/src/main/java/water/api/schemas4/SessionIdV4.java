package water.api.schemas4;

import water.Iced;
import water.api.API;

public class SessionIdV4 extends OutputSchemaV4<Iced, SessionIdV4> {

  @API(help="Session ID")
  public String session_key;

}
