package water.api.schemas4;

import water.Iced;
import water.api.API;
import water.api.Schema;

/**
 * Base Schema class for all v4 REST API requests. It provides common _schema field, as well as .
 */
public class OutputSchemaV4<I extends Iced, S extends OutputSchemaV4<I,S>> extends Schema<I,S> {

  @API(help="Url describing the schema of the current object.")
  public String _schema;


  public OutputSchemaV4() {
    _schema = "/4/schemas/" + this.getSchemaName();
  }
}
