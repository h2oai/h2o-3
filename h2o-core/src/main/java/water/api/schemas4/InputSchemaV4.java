package water.api.schemas4;

import water.Iced;
import water.api.API;
import water.api.Schema;

/**
 * Base Schema class for all v4 REST API requests. It provides common _schema field, as well as .
 */
public class InputSchemaV4<I extends Iced, S extends InputSchemaV4<I,S>> extends Schema<I,S> {

  @API(help="Filter on the set of output fields: if you set _fields=\"foo,bar,baz\", then only those fields will be " +
      "included in the output; or you can specify _fields=\"-goo,gee\" to include all fields except goo and gee. If " +
      "the result contains nested data structures, then you can refer to the fields within those structures as well. " +
      "For example if you specify _fields=\"foo(oof),bar(-rab)\", then only fields foo and bar will be included, and " +
      "within foo there will be only field oof, whereas within bar all fields except rab will be reported.")
  public String _fields;
}
