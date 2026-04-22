package water.webserver.jetty12;

import org.eclipse.jetty.ee8.nested.Handler;
import org.eclipse.jetty.ee8.nested.HandlerCollection;
import org.eclipse.jetty.ee8.nested.HandlerWrapper;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import water.webserver.iface.Credentials;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.ProxyServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

class Jetty12ProxyServerAdapter implements ProxyServer {
  private final Jetty12Helper helper;
  private final H2OHttpView h2oHttpView;
  private final Credentials credentials;
  private final String proxyTo;

  private Jetty12ProxyServerAdapter(Jetty12Helper helper, H2OHttpView h2oHttpView, Credentials credentials, String proxyTo) {
    this.helper = helper;
    this.h2oHttpView = h2oHttpView;
    this.credentials = credentials;
    this.proxyTo = proxyTo;
  }

  static ProxyServer create(final H2OHttpView h2oHttpView, final Credentials credentials, final String proxyTo) {
    final Jetty12Helper helper = new Jetty12Helper(h2oHttpView);
    return new Jetty12ProxyServerAdapter(helper, h2oHttpView, credentials, proxyTo);
  }

  @Override
  public void start(final String ip, final int port) throws IOException {
    final Server jettyServer = helper.createJettyServer(ip, port);
    final HandlerWrapper handlerWrapper = helper.authWrapper(jettyServer);
    final ServletContextHandler context = helper.createServletContextHandler();
    registerHandlers(handlerWrapper, context, credentials, proxyTo);
    try {
      jettyServer.start();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  private void registerHandlers(HandlerWrapper handlerWrapper, ServletContextHandler context, Credentials credentials, String proxyTo) {
    // setup authenticating proxy servlet (each request is forwarded with BASIC AUTH)
    final ServletHolder proxyServlet = new ServletHolder(TransparentProxyServlet.class);
    proxyServlet.setInitParameter("proxyTo", proxyTo);
    proxyServlet.setInitParameter("Prefix", "/");
    proxyServlet.setInitParameter("BasicAuth", credentials.toBasicAuth());
    context.addServlet(proxyServlet, "/*");
    // authHandlers assume the user is already authenticated
    final HandlerCollection authHandlers = new HandlerCollection();
    authHandlers.setHandlers(new Handler[]{
        helper.authenticationHandler(),
        context,
    });
    final ProxyLoginHandler loginHandler = new ProxyLoginHandler();
    loginHandler.setHandler(authHandlers);
    handlerWrapper.setHandler(loginHandler);
  }

  private class ProxyLoginHandler extends HandlerWrapper {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      final boolean handled = h2oHttpView.proxyLoginHandler(target, request, response);
      if (handled) {
        baseRequest.setHandled(true);
      } else {
        super.handle(target, baseRequest, request, response);
      }
    }
  }
}
