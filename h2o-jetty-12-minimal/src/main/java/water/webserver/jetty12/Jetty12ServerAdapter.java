package water.webserver.jetty12;

import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.Handler;
import org.eclipse.jetty.ee8.nested.HandlerCollection;
import org.eclipse.jetty.ee8.nested.HandlerWrapper;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.servlet.ServletHolder;
import org.eclipse.jetty.server.Server;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.H2OWebsocketServlet;
import water.webserver.iface.RequestAuthExtension;
import water.webserver.iface.WebServer;

import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

class Jetty12ServerAdapter implements WebServer {
  private final Jetty12Helper helper;
  private final H2OHttpView h2oHttpView;
  private Server jettyServer;

  private Jetty12ServerAdapter(Jetty12Helper helper, H2OHttpView h2oHttpView) {
    this.helper = helper;
    this.h2oHttpView = h2oHttpView;
  }

  static WebServer create(final H2OHttpView h2oHttpView) {
    final Jetty12Helper helper = new Jetty12Helper(h2oHttpView);
    return new Jetty12ServerAdapter(helper, h2oHttpView);
  }

  @Override
  public void start(final String ip, final int port) throws IOException {
    jettyServer = helper.createJettyServer(ip, port);
    final HandlerWrapper handlerWrapper = helper.authWrapper(jettyServer);
    final ServletContextHandler context = helper.createServletContextHandler();
    registerHandlers(handlerWrapper, context);
    // TODO(GH-16810): wire gate / auth / authExtensions as servlet filters on the ServletContextHandler
    // (Jetty 12 Server only accepts core Handler; ee8.nested.HandlerCollection can't be attached directly).
    // For now bind the ServletContextHandler as the Server's root so /3/* endpoints respond; the
    // ee8 handler chain built in registerHandlers is currently not reached on the wire.
    jettyServer.setHandler(context);
    try {
      jettyServer.start();
    } catch (IOException e) {
      throw e;
    } catch (Exception e) {
      throw new IOException(e);
    }
  }

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
    for (Map.Entry<String, Class<? extends H2OWebsocketServlet>> entry : h2oHttpView.getWebsockets().entrySet()) {
      try {
        Servlet servlet = new Jetty12WebsocketServlet(entry.getValue().newInstance());
        context.addServlet(new ServletHolder(entry.getValue().getName(), servlet), entry.getKey());
      } catch (InstantiationException | IllegalAccessException e) {
        throw new RuntimeException("Failed to instantiate websocket servlet object", e);
      }
    }

    final List<Handler> extHandlers = new ArrayList<>();
    extHandlers.add(helper.authenticationHandler());
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
    extHandlers.add(context);

    final HandlerCollection authHandlers = new HandlerCollection();
    authHandlers.setHandlers(extHandlers.toArray(new Handler[0]));

    final GateHandler gateHandler = new GateHandler();
    gateHandler.setHandler(authHandlers);

    handlerWrapper.setHandler(gateHandler);
  }

  private class GateHandler extends HandlerWrapper {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      final boolean handled = h2oHttpView.gateHandler(request, response);
      if (handled) {
        baseRequest.setHandled(true);
      } else {
        super.handle(target, baseRequest, request, response);
      }
    }
  }
}
