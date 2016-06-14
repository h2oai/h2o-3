package water.api;

import water.Iced;

public class MetadataBase<I extends Iced, S extends MetadataBase<I, S>> extends SchemaV3<I, MetadataBase<I, S>> {
  @API(help="Number for specifying an endpoint", json=false)
  public int num;

  @API(help="HTTP method (GET, POST, DELETE) if fetching by path", json=false)
  public String http_method;

  @API(help="Path for specifying an endpoint", json=false)
  public String path;

  @API(help="Class name, for fetching docs for a schema (DEPRECATED)", json=false)
  public String classname;

  @API(help="Schema name (e.g., DocsV1), for fetching docs for a schema", json=false)
  public String schemaname;

  // Outputs
  @API(help="List of endpoint routes", direction=API.Direction.OUTPUT)
  public RouteBase[] routes;

  @API(help="List of schemas", direction=API.Direction.OUTPUT)
  public SchemaMetadataBase[] schemas;

  @API(help="Table of Contents Markdown", direction=API.Direction.OUTPUT)
  public String markdown;
}
