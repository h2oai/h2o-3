package water.api;

import water.util.PojoUtils;

public class RouteBase<I extends Route, S extends RouteBase<I, S>> extends SchemaV3<I, RouteBase<I, S>> {
  @API(help="", direction=API.Direction.OUTPUT)
  public String  http_method;

  @API(help="", direction=API.Direction.OUTPUT)
  public String url_pattern;

  @API(help="", direction=API.Direction.OUTPUT)
  public String summary;

  @API(help="", direction=API.Direction.OUTPUT)
  public String api_name;

  @API(help="", direction=API.Direction.OUTPUT)
  public String handler_class;

  @API(help="", direction=API.Direction.OUTPUT)
  public String handler_method;

  @API(help="", direction=API.Direction.OUTPUT)
  public String input_schema;

  @API(help="", direction=API.Direction.OUTPUT)
  public String output_schema;

  // NOTE: Java 7 captures and lets you look up subpatterns by name but won't give you the list of names, so we need this redundant list:
  @API(help="", direction=API.Direction.OUTPUT)
  public String[] path_params; // list of params we capture from the url pattern, e.g. for /17/MyComplexObj/(.*)/(.*)

  @API(help="", direction=API.Direction.OUTPUT)
  public String markdown;

  //@Override
  public S fillFromImpl(Route impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.ORIGIN_HAS_UNDERSCORES, new String[] {"url_pattern", "handler_class", "handler_method", "doc_method"} );
    this.url_pattern = impl._url;
    this.handler_class = impl._handler_class.toString();
    this.handler_method = impl._handler_method.getName();
    this.input_schema = Handler.getHandlerMethodInputSchema(impl._handler_method).getSimpleName();
    this.output_schema = Handler.getHandlerMethodOutputSchema(impl._handler_method).getSimpleName();
    return (S)this;
  }
}
