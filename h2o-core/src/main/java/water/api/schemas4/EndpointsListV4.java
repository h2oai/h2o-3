package water.api.schemas4;

import water.Iced;
import water.api.API;

/**
 * List of endpoints, returned by GET /4/endpoints
 */
public class EndpointsListV4 extends OutputSchemaV4<Iced, EndpointsListV4> {

  @API(help="List of endpoints in H2O REST API (v4).")
  public EndpointV4[] endpoints;

}
