package water.webserver.jetty12;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.Request;
import org.eclipse.jetty.client.transport.HttpClientTransportOverHTTP;
import org.eclipse.jetty.ee8.proxy.ProxyServlet;
import org.eclipse.jetty.io.ClientConnector;
import org.eclipse.jetty.util.ssl.SslContextFactory;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;


/**
 * Transparent proxy that automatically adds BASIC authentication to each proxied request.
 */
public class TransparentProxyServlet extends ProxyServlet.Transparent {
  private String _basicAuth;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    _basicAuth = config.getInitParameter("BasicAuth");
  }

  @Override
  protected HttpClient newHttpClient() {
    final SslContextFactory.Client sslContextFactory = new SslContextFactory.Client();
    sslContextFactory.setTrustAll(true);
    final ClientConnector connector = new ClientConnector();
    connector.setSslContextFactory(sslContextFactory);
    return new HttpClient(new HttpClientTransportOverHTTP(connector));
  }

  @Override
  protected void addProxyHeaders(HttpServletRequest clientRequest,
                                 Request proxyRequest) {
    proxyRequest.headers(h -> {
      h.remove("Authorization");
      h.put("Authorization", _basicAuth);
    });
  }
}
