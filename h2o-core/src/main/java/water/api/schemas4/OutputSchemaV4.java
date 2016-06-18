package water.api.schemas4;

import water.Iced;
import water.api.API;
import water.api.Schema;

/**
 * Base output Schema class for all v4 REST API requests. It provides common __schema field that identifies the
 * schema in the output.
 */
public class OutputSchemaV4<I extends Iced, S extends OutputSchemaV4<I,S>> extends Schema<I,S> {

  @API(help="Url describing the schema of the current object.")
  public String __schema;


  public OutputSchemaV4() {
    __schema = "/4/schemas/" + this.getSchemaName();
  }
}
