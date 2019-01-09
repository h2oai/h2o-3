package water.webserver.iface;

/**
 * Facade for an HTTP server implementation. We typically use Jetty behind this; however, due to use of various major versions,
 * we cannot afford anymore to depend on Jetty directly; the changes between its major versions are as significant
 * as if it was a completely different webserver.
 *
 * This interface is supposed to hide all those dependencies.
 */
public interface HttpServerFacade {
  /**
   * @param h2oHttpView a partial view of H2O's functionality
   * @return a new instance of web server adapter
   */
  WebServer createWebServer(H2OHttpView h2oHttpView);

  /**
   * @param h2oHttpView a partial view of H2O's functionality
   * @param credentials -
   * @param proxyTo -
   * @return a new instance of web proxy adapter
   */
  ProxyServer createProxyServer(H2OHttpView h2oHttpView, Credentials credentials, String proxyTo);
}
