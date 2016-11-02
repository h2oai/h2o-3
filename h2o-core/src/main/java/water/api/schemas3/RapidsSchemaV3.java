package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsSchemaV3<I extends Iced, S extends RapidsSchemaV3<I, S>> extends RequestSchemaV3<I, S> {

  @API(help="A Rapids AstRoot expression", direction=API.Direction.INPUT, required=true)
  public String ast;

  @API(help="Session key", direction=API.Direction.INPUT)
  public String session_id;

  @API(help="[DEPRECATED] Key name to assign Frame results", direction=API.Direction.INPUT)
  public String id;

}
