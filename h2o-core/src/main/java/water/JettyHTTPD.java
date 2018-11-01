package water;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import water.api.DatasetServlet;
import water.api.NpsBinServlet;
import water.api.PostFileServlet;
import water.api.PutKeyServlet;
import water.api.RequestServer;
import water.server.RequestAuthExtension;
import water.server.ServletUtils;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Embedded Jetty instance inside H2O.
 * This is intended to be a singleton per H2O node.
 */
public class JettyHTTPD extends AbstractHTTPD {
  //------------------------------------------------------------------------------------------
  // Object-specific things.
  //------------------------------------------------------------------------------------------
  private static volatile boolean _acceptRequests = false;

  /**
   * Create bare Jetty object.
   */
  public JettyHTTPD() {
    super(H2O.ARGS);
  }

  public void acceptRequests() {
    _acceptRequests = true;
  }

  @Override
  protected RuntimeException failEx(String message) {
    return H2O.fail(message);
  }

  @Override
  protected void registerHandlers(HandlerWrapper handlerWrapper, ServletContextHandler context) {
    context.addServlet(NpsBinServlet.class,   "/3/NodePersistentStorage.bin/*");
    context.addServlet(PostFileServlet.class, "/3/PostFile.bin");
    context.addServlet(PostFileServlet.class, "/3/PostFile");
    context.addServlet(DatasetServlet.class,  "/3/DownloadDataset");
    context.addServlet(DatasetServlet.class,  "/3/DownloadDataset.bin");
    context.addServlet(PutKeyServlet.class,   "/3/PutKey.bin");
    context.addServlet(PutKeyServlet.class,   "/3/PutKey");
    context.addServlet(RequestServer.class,   "/");

    final List<Handler> extHandlers = new ArrayList<Handler>();
    extHandlers.add(new AuthenticationHandler());
    // here we wrap generic authentication handlers into jetty-aware wrappers
    for (final RequestAuthExtension requestAuthExtension : ExtensionManager.getInstance().getAuthExtensions()) {
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
    HandlerCollection authHandlers = new HandlerCollection();
    authHandlers.setHandlers(extHandlers.toArray(new Handler[extHandlers.size()]));

    // LoginHandler handles directly login requests and delegates the rest to the authHandlers
    LoginHandler loginHandler = new LoginHandler("/login", "/loginError");
    loginHandler.setHandler(authHandlers);

    HandlerCollection hc = new HandlerCollection();
    hc.setHandlers(new Handler[]{
            new GateHandler(),
            loginHandler
    });
    handlerWrapper.setHandler(hc);
  }

  public class GateHandler extends AbstractHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response) {
      ServletUtils.startRequestLifecycle();
      while (!_acceptRequests) {
        try { Thread.sleep(100); }
        catch (Exception ignore) {}
      }

      boolean isXhrRequest = false;
      if (request != null) {
        isXhrRequest = isXhrRequest(request);
      }
      setCommonResponseHttpHeaders(response, isXhrRequest);
    }
  }

  @Override
  protected void sendUnauthorizedResponse(HttpServletResponse response, String message) throws IOException {
    ServletUtils.sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, message);
  }

  public class LoginHandler extends HandlerWrapper {
    private String _loginTarget;
    private String _errorTarget;
    public LoginHandler(String loginTarget, String errorTarget) {
      _loginTarget = loginTarget;
      _errorTarget = errorTarget;
    }
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException, ServletException {
      if (isLoginTarget(target)) {
        if (isPageRequest(request))
          sendLoginForm(request, response);
        else
          ServletUtils.sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, "Access denied. Please login.");
        baseRequest.setHandled(true);
      } else {
        // not for us, invoke wrapped handler
        super.handle(target, baseRequest, request, response);
      }
    }
    private void sendLoginForm(HttpServletRequest request, HttpServletResponse response) {
      String uri = ServletUtils.getDecodedUri(request);
      try {
        byte[] bytes;
        try (InputStream resource = water.init.JarHash.getResource2("/login.html")) {
          if (resource == null)
            throw new IllegalStateException("Login form not found");
          ByteArrayOutputStream baos = new ByteArrayOutputStream();
          water.util.FileUtils.copyStream(resource, baos, 2048);
          bytes = baos.toByteArray();
        }
        response.setContentType(RequestServer.MIME_HTML);
        response.setContentLength(bytes.length);
        ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
        OutputStream os = response.getOutputStream();
        water.util.FileUtils.copyStream(new ByteArrayInputStream(bytes), os, 2048);
      } catch (Exception e) {
        ServletUtils.sendErrorResponse(response, e, uri);
      } finally {
        ServletUtils.logRequest("GET", request, response);
      }
    }
    private boolean isPageRequest(HttpServletRequest request) {
      String accept = request.getHeader("Accept");
      return (accept != null) && accept.contains(RequestServer.MIME_HTML);
    }
    private boolean isLoginTarget(String target) {
      return target.equals(_loginTarget) || target.equals(_errorTarget);
    }
  }

  private static boolean isXhrRequest(final HttpServletRequest request) {
    final String requestedWithHeader = request.getHeader("X-Requested-With");
    return "XMLHttpRequest".equals(requestedWithHeader);
  }

  private static void setCommonResponseHttpHeaders(HttpServletResponse response, final boolean xhrRequest) {
    if (xhrRequest) {
      response.setHeader("Cache-Control", "no-cache");
    }
    response.setHeader("X-h2o-build-project-version", H2O.ABV.projectVersion());
    response.setHeader("X-h2o-rest-api-version-max", Integer.toString(water.api.RequestServer.H2O_REST_API_VERSION));
    response.setHeader("X-h2o-cluster-id", Long.toString(H2O.CLUSTER_ID));
    response.setHeader("X-h2o-cluster-good", Boolean.toString(H2O.CLOUD.healthy()));
    response.setHeader("X-h2o-context-path", sanatizeContextPath(H2O.ARGS.context_path));
  }

  private static String sanatizeContextPath(String context_path) {
    if(null == context_path || context_path.isEmpty()) {
      return "/";
    }
    return context_path + "/";
  }


  //--------------------------------------------------

}
