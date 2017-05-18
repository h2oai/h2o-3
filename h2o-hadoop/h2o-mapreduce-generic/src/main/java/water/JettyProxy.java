package water;

import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.ProxyServlet;

public class JettyProxy extends AbstractHTTPD {

  private String _proxyTo;

  public JettyProxy(H2O.BaseArgs args, String proxyTo) {
    super(args);
    _proxyTo = proxyTo;
  }

  @Override
  protected void registerHandlers(HandlerWrapper handlerWrapper, ServletContextHandler context) {
    ServletHolder proxyServlet = new ServletHolder(ProxyServlet.Transparent.class);
    proxyServlet.setInitParameter("ProxyTo", _proxyTo);
    proxyServlet.setInitParameter("Prefix", "/");
    context.addServlet(proxyServlet, "/*");
    handlerWrapper.setHandler(context);
  }

  @Override
  protected RuntimeException failEx() {
    return new IllegalStateException("Proxy configuration incorrect");
  }

}
