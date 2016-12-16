package water.api.schemas4.input;

import water.Iced;
import water.api.API;
import water.api.schemas4.InputSchemaV4;

/**
 */
public class CascadeSessionIV4 extends InputSchemaV4<Iced, CascadeSessionIV4> {

  @API(help="Indicate which user starts the session.")
  public String user;

}
