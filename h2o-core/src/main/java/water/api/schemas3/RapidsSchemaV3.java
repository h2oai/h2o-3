package water.api.schemas3;

import water.Iced;
import water.api.API;

/**
 */
public class RapidsSchemaV3<I extends Iced, S extends RapidsSchemaV3<I, S>> extends SchemaV3<I, S> {

  @API(help="A Rapids AST expression", direction=API.Direction.INPUT, required=true)
  public String ast;

<<<<<<< 11c9bc6ca4a8d60eee8dea3bf03d145011e126e8
=======
  @API(help="[DEPRECATED] Key name to assign Frame results", direction=API.Direction.INPUT)
  public String id;

>>>>>>> Mark field id in RapidsSchemaV3 as deprecated -- should always be null
  @API(help="Session key", direction=API.Direction.INPUT)
  public String session_id;

  @API(help="[DEPRECATED] Key name to assign Frame results", direction=API.Direction.INPUT)
  public String id;

}
