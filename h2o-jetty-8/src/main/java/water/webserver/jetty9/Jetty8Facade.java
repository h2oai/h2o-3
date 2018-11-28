package water.webserver.jetty9;

import water.webserver.iface.Credentials;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.HttpServerFacade;
import water.webserver.iface.ProxyServer;
import water.webserver.iface.WebServer;

public class Jetty8Facade implements HttpServerFacade {
  @Override
  public WebServer createWebServer(H2OHttpView h2oHttpView) {
    return Jetty8ServerAdapter.create(h2oHttpView);
  }

  @Override
  public ProxyServer createProxyServer(H2OHttpView h2oHttpView, Credentials credentials, String proxyTo) {
    return Jetty8ProxyServerAdapter.create(h2oHttpView, credentials, proxyTo);
  }
}
