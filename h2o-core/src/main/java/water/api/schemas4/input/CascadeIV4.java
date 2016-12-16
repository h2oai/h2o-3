package water.api.schemas4.input;

import water.Iced;
import water.api.API;
import water.api.schemas4.InputSchemaV4;

/**
 * Input schema for {@link ai.h2o.cascade.CascadeHandlers.Run}.
 */
public class CascadeIV4 extends InputSchemaV4<Iced, CascadeIV4> {

  @API(help="Cascade expression to execute.")
  public String cascade;

  @API(help="Id of the session within which to evaluate the expression.")
  public String session_id;

}
