package water.api.schemas3;

import water.Iced;
import water.api.API;

public final class MetadataV3 extends SchemaV3<Iced, MetadataV3> {
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
  public RouteV3[] routes;

  @API(help="List of schemas", direction=API.Direction.OUTPUT)
  public SchemaMetadataV3[] schemas;

  @API(help="Table of Contents Markdown", direction=API.Direction.OUTPUT)
  public String markdown;

}
