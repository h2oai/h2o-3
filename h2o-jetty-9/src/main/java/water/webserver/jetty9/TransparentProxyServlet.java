package water.webserver.jetty9;

import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.proxy.ProxyServlet;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.eclipse.jetty.client.api.Request;


/**
 * Transparent proxy that automatically adds authentication to each request
 */
public class TransparentProxyServlet extends ProxyServlet.Transparent {
  private String _basicAuth;

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);
    _basicAuth = config.getInitParameter("BasicAuth");
  }

  @Override
  protected void addProxyHeaders(HttpServletRequest clientRequest,
                                 Request proxyRequest) {
    proxyRequest.getHeaders().remove("Authorization");
    proxyRequest.header("Authorization", _basicAuth);
  }
}
