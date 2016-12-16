package water.api.schemas4.input;

import water.Iced;
import water.api.API;
import water.api.schemas4.InputSchemaV4;

/**
 * Input schema for {@code DELETE /4/sessions/{session_id}}
 */
public class CascadeCloseSessionIV4 extends InputSchemaV4<Iced, CascadeCloseSessionIV4> {

  @API(help="Id of the session to close.")
  public String session_id;

}
