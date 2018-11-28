package water.webserver.jetty9;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.RequestAuthExtension;
import water.webserver.iface.WebServer;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class Jetty8ServerAdapter implements WebServer {
  private final Jetty8Helper helper;
  private final H2OHttpView h2oHttpView;
  private Server jettyServer;

  private Jetty8ServerAdapter(Jetty8Helper helper, H2OHttpView h2oHttpView) {
    this.helper = helper;
    this.h2oHttpView = h2oHttpView;
  }

  static WebServer create(final H2OHttpView h2oHttpView) {
    final Jetty8Helper helper = new Jetty8Helper(h2oHttpView);
    return new Jetty8ServerAdapter(helper, h2oHttpView);
  }

  @Override
  public void start(final String ip, final int port) throws IOException {
    jettyServer = helper.createJettyServer(ip, port);
    final HandlerWrapper handlerWrapper = helper.authWrapper(jettyServer);
    final ServletContextHandler context = helper.createServletContextHandler();
    registerHandlers(handlerWrapper, context);
    try {
      jettyServer.start();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

  /**
   * Stop Jetty server after it has been started.
   * This is unlikely to ever be called by H2O until H2O supports graceful shutdown.
   *
   * @throws Exception -
   */
  @Override
  public void stop() throws IOException {
    if (jettyServer != null) {
      try {
        jettyServer.stop();
      } catch (IOException e) {
        throw e;
      } catch (Exception e) {
        throw new IOException(e);
      }
    }
  }

  private void registerHandlers(final HandlerWrapper handlerWrapper, final ServletContextHandler context) {
    for (Map.Entry<String, Class<? extends HttpServlet>> entry : h2oHttpView.getServlets().entrySet()) {
      context.addServlet(entry.getValue(), entry.getKey());
    }

    final List<Handler> extHandlers = new ArrayList<>();
    extHandlers.add(helper.authenticationHandler());
    // here we wrap generic authentication handlers into jetty-aware wrappers
    final Collection<RequestAuthExtension> authExtensions = h2oHttpView.getAuthExtensions();
    for (final RequestAuthExtension requestAuthExtension : authExtensions) {
      extHandlers.add(new AbstractHandler() {
        @Override
        public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
          if (requestAuthExtension.handle(target, request, response)) {
            baseRequest.setHandled(true);
          }
        }
      });
    }
    //
    extHandlers.add(context);

    // Handlers that can only be invoked for an authenticated user (if auth is enabled)
    final HandlerCollection authHandlers = new HandlerCollection();
    authHandlers.setHandlers(extHandlers.toArray(new Handler[0]));

    // LoginHandler handles directly login requests and delegates the rest to the authHandlers
    final LoginHandler loginHandler = new LoginHandler();
    loginHandler.setHandler(authHandlers);

    final HandlerCollection hc = new HandlerCollection();
    hc.setHandlers(new Handler[]{
        new GateHandler(),
        loginHandler
    });
    handlerWrapper.setHandler(hc);
  }

  private class LoginHandler extends HandlerWrapper {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      final boolean handled = h2oHttpView.loginHandler(target, request, response);
      if (handled) {
        baseRequest.setHandled(true);
      } else {
        super.handle(target, baseRequest, request, response);
      }
    }
  }

  private class GateHandler extends AbstractHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
      h2oHttpView.gateHandler(request, response);
    }
  }
}
