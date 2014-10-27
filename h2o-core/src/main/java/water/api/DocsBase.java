package water.api;

import water.util.PojoUtils;

public class DocsBase extends Schema<DocsHandler.DocsPojo, DocsBase> {
  @API(help="Number for specifying an endpoint", json=false)
  public int num;

  @API(help="HTTP method (GET, POST, DELETE) if fetching by path", json=false)
  public String http_method;

  @API(help="Path for specifying an endpoint", json=false)
  public String path;

  @API(help="Class name, for fetching docs for a schema", json=false)
  public String classname;

  // Outputs
  @API(help="List of endpoint routes.")
  public RouteBase[] routes;

  @API(help="List of schemas.")
  public SchemaMetadataBase[] schemas;

  @Override public DocsHandler.DocsPojo createImpl() {
    DocsHandler.DocsPojo impl = new DocsHandler.DocsPojo();
    // NOTE: we don't currently have a need to take the routes and schemas in the reverse direction
    PojoUtils.copyProperties(impl, this, PojoUtils.FieldNaming.CONSISTENT/*, new String[] { "routes", "schemas" }*/);
    return impl;
  }

  @Override public DocsBase fillFromImpl(DocsHandler.DocsPojo impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT, new String[] { "routes", "schemas" });
    this.routes = new RouteBase[null == impl.routes ? 0 : impl.routes.length];
    this.schemas = new SchemaMetadataBase[null == impl.schemas ? 0 : impl.schemas.length];

    if (null != impl.routes) {
      int i = 0;
      for (Route r : impl.routes) {
        this.routes[i++] = new RouteV1().fillFromImpl(r); // TODO: version!
      }
    }
    if (null != impl.schemas) {
      int i = 0;
      for (SchemaMetadata m : impl.schemas) {
        this.schemas[i++] = new SchemaMetadataV1().fillFromImpl(m); // TODO: version!
      }
    }
    return this;
  }
}
