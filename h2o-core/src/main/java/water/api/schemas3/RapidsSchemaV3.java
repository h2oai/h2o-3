package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsSchemaV3<I extends Iced, S extends RapidsSchemaV3<I, S>> extends SchemaV3<I, S> {

  @API(help="An Abstract Syntax Tree.", direction=API.Direction.INPUT)
  public String ast;

  @API(help="[DEPRECATED] Key name to assign Frame results", direction=API.Direction.INPUT)
  public String id;

  @API(help="Session key", direction=API.Direction.INPUT)
  public String session_id;

}
