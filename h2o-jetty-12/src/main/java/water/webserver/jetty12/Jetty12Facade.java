package water.webserver.jetty12;

import water.webserver.iface.Credentials;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.HttpServerFacade;
import water.webserver.iface.ProxyServer;
import water.webserver.iface.WebServer;

public class Jetty12Facade implements HttpServerFacade {
  @Override
  public WebServer createWebServer(H2OHttpView h2oHttpView) {
    return Jetty12ServerAdapter.create(h2oHttpView);
  }

  @Override
  public ProxyServer createProxyServer(H2OHttpView h2oHttpView, Credentials credentials, String proxyTo) {
    return Jetty12ProxyServerAdapter.create(h2oHttpView, credentials, proxyTo);
  }
}
