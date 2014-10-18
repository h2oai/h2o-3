package water.api;

import water.util.PojoUtils;

public class DocsBase extends Schema<DocsHandler.DocsPojo, DocsBase> {
  @API(help="Number for specifying an endpoint", json=false)
    int num;

  @API(help="HTTP method (GET, POST, DELETE) if fetching by path", json=false)
    String http_method;

  @API(help="Path for specifying an endpoint", json=false)
    String path;

  // Outputs
  @API(help="List of endpoint routes.")
    RouteBase[] routes;

  @Override public DocsHandler.DocsPojo createImpl() {
    DocsHandler.DocsPojo i = new DocsHandler.DocsPojo();
    return i;
  }

  @Override public DocsBase fillFromImpl(DocsHandler.DocsPojo impl) {
    PojoUtils.copyProperties(this, impl, PojoUtils.FieldNaming.CONSISTENT);
    this.routes = new RouteBase[impl.routes.length];

    int i = 0;
    for (Route r : impl.routes) {
      this.routes[i++] = new RouteV1().fillFromImpl(r);
    }
    return this;
  }
}
