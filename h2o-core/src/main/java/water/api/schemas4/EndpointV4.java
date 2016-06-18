package water.api.schemas4;

import water.api.API;
import water.api.Handler;
import water.api.Route;

/**
 * 
 */
public class EndpointV4 extends OutputSchemaV4<Route, EndpointV4> {

  @API(help="Http method of the request: GET/POST/PUT/DELETE")
  public String  http_method;

  @API(help="Url of the request; variable parts are enclosed in curly braces. For example: /4/schemas/{schema_name}")
  public String url_pattern;

  @API(help="Short description of the functionality provided by the endpoint.")
  public String description;

  @API(help="Unique name of the endpoint. These names can be used to look up the endpoint's info via " +
      "GET /4/endpoints/{name}.")
  public String name;

  // TODO: more explanation -- how input object corresponds to the actual request
  @API(help="Input schema.")
  public String input_schema;

  @API(help="Schema for the result returned by the endpoint.")
  public String output_schema;

  @Override
  public EndpointV4 fillFromImpl(Route route) {
    http_method = route._http_method;
    url_pattern = route._url;
    description = route._summary;
    name = route._api_name;
    input_schema = "/4/schemas/" + Handler.getHandlerMethodInputSchema(route._handler_method).getSimpleName();
    output_schema = "/4/schemas/" + Handler.getHandlerMethodOutputSchema(route._handler_method).getSimpleName();
    return this;
  }
}
