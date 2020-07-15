package water.webserver;

import org.apache.commons.io.IOUtils;
import water.ExtensionManager;
import water.api.RequestServer;
import water.server.ServletService;
import water.server.ServletUtils;
import water.util.Log;
import water.webserver.iface.*;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Collection;
import java.util.LinkedHashMap;

/**
 * This is intended to be a singleton per H2O node.
 */
public class H2OHttpViewImpl implements H2OHttpView {
  private static volatile boolean _acceptRequests = false;
  private final H2OHttpConfig config;

  public H2OHttpViewImpl(H2OHttpConfig config) {
    this.config = config;
  }

  public void acceptRequests() {
    _acceptRequests = true;
  }

  /**
   * @return URI scheme
   */
  public String getScheme() {
    return config.jks == null ? "http" : "https";
  }

  @Override
  public LinkedHashMap<String, Class<? extends HttpServlet>> getServlets() {
    return ServletService.INSTANCE.getAllServlets();
  }

  @Override
  public LinkedHashMap<String, Class<? extends H2OWebsocketServlet>> getWebsockets() {
    return ServletService.INSTANCE.getAllWebsockets();
  }

  @Override
  public boolean authenticationHandler(HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (!config.loginType.needToCheckUserName()) {
      return false;
    }
    
    if (request.getUserPrincipal() == null) {
      throw new IllegalStateException("AuthenticateHandler called with request.userPrincipal is null");
    }
  
    final String loginName = request.getUserPrincipal().getName();
    if (loginName.equals(config.user_name)) {
      return false;
    } else {
      Log.warn("Login name (" + loginName + ") does not match cluster owner name (" + config.user_name + ")");
      ServletUtils.sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, "Login name does not match cluster owner name");
      return true;
    }
  }

  @Override
  public boolean gateHandler(HttpServletRequest request, HttpServletResponse response) {
    ServletUtils.startRequestLifecycle();
    while (! isAcceptingRequests()) {
      try { Thread.sleep(100); }
      catch (Exception ignore) {}
    }

    boolean isXhrRequest = false;
    if (request != null) {
      if (ServletUtils.isTraceRequest(request)) {
        ServletUtils.setResponseStatus(response, HttpServletResponse.SC_METHOD_NOT_ALLOWED);
        return true;
      }
      isXhrRequest = ServletUtils.isXhrRequest(request);
    }
    ServletUtils.setCommonResponseHttpHeaders(response, isXhrRequest);
    return false;
  }

  protected boolean isAcceptingRequests() {
    return _acceptRequests;
  }
  
  @Override
  public H2OHttpConfig getConfig() {
    return config;
  }

  @Override
  public Collection<RequestAuthExtension> getAuthExtensions() {
    return ExtensionManager.getInstance().getAuthExtensions();
  }


  // normal login handler part //todo: consider using mostly the same code as in proxy part below

  @Override
  public boolean loginHandler(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (! isLoginTarget(target)) {
      return false;
    }

    if (isPageRequest(request)) {
      sendLoginForm(request, response);
    } else {
      ServletUtils.sendResponseError(response, HttpServletResponse.SC_UNAUTHORIZED, "Access denied. Please login.");
    }
    return true;
  }

  private static void sendLoginForm(HttpServletRequest request, HttpServletResponse response) {
    final String uri = ServletUtils.getDecodedUri(request);
    try {
      byte[] bytes;
      try (InputStream resource = water.init.JarHash.getResource2("/login.html")) {
        if (resource == null) {
          throw new IllegalStateException("Login form not found");
        }
        final ByteArrayOutputStream baos = new ByteArrayOutputStream();
        water.util.FileUtils.copyStream(resource, baos, 2048);
        bytes = baos.toByteArray();
      }
      response.setContentType(RequestServer.MIME_HTML);
      response.setContentLength(bytes.length);
      ServletUtils.setResponseStatus(response, HttpServletResponse.SC_OK);
      final OutputStream os = response.getOutputStream();
      water.util.FileUtils.copyStream(new ByteArrayInputStream(bytes), os, 2048);
    } catch (Exception e) {
      ServletUtils.sendErrorResponse(response, e, uri);
    } finally {
      ServletUtils.logRequest("GET", request, response);
    }
  }

  private static boolean isPageRequest(HttpServletRequest request) {
    String accept = request.getHeader("Accept");
    return (accept != null) && accept.contains(RequestServer.MIME_HTML);
  }

  private static boolean isLoginTarget(String target) {
    return target.equals("/login") || target.equals("/loginError");
  }

  // proxy login handler part

  @Override
  public boolean proxyLoginHandler(String target, HttpServletRequest request, HttpServletResponse response) throws IOException {
    if (! isLoginTarget(target)) {
      return false;
    }

    if (isPageRequest(request)) {
      proxySendLoginForm(response);
    } else {
      response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Access denied. Please login.");
    }
    return true;
  }

  private static byte[] proxyLoadLoginFormResource() throws IOException {
    final InputStream loginFormStream = H2OHttpView.class.getResourceAsStream("/www/login.html");
    if (loginFormStream == null) {
      throw new IllegalStateException("Login form resource is missing.");
    }
    final ByteArrayOutputStream baos = new ByteArrayOutputStream();
    IOUtils.copy(loginFormStream, baos);
    return baos.toByteArray();
  }

  private byte[] proxyLoginFormData;

  private void proxySendLoginForm(HttpServletResponse response) throws IOException {
    if (proxyLoginFormData == null) {
      proxyLoginFormData = proxyLoadLoginFormResource();
    }
    response.setContentType("text/html");
    response.setContentLength(proxyLoginFormData.length);
    response.setStatus(HttpServletResponse.SC_OK);
    IOUtils.write(proxyLoginFormData, response.getOutputStream());
  }

}
