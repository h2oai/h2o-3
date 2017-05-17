package water;

import org.apache.commons.io.IOUtils;
import org.eclipse.jetty.proxy.ProxyServlet;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.security.Credential;
import water.network.SecurityUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class JettyProxy extends AbstractHTTPD {

  private final String _proxyTo;
  private final Credentials _credentials;

  public JettyProxy(H2O.BaseArgs args, Credentials credentials, String proxyTo) {
    super(args);
    _proxyTo = proxyTo;
    _credentials = credentials;
  }

  @Override
  protected void registerHandlers(HandlerWrapper handlerWrapper, ServletContextHandler context) {
    // setup authenticating proxy servlet (each request is forwarded with BASIC AUTH)
    ServletHolder proxyServlet = new ServletHolder(Transparent.class);
    proxyServlet.setInitParameter("ProxyTo", _proxyTo);
    proxyServlet.setInitParameter("Prefix", "/");
    proxyServlet.setInitParameter("BasicAuth", _credentials.toBasicAuth());
    context.addServlet(proxyServlet, "/*");
    // authHandlers assume the user is already authenticated
    HandlerCollection authHandlers = new HandlerCollection();
    authHandlers.setHandlers(new Handler[]{
            new AuthenticationHandler(),
            context,
    });
    // handles requests of login form and delegates the rest to the authHandlers
    LoginHandler loginHandler = new LoginHandler("/login", "/loginError");
    loginHandler.setHandler(authHandlers);
    // login handler is the root handler
    handlerWrapper.setHandler(loginHandler);
  }

  @Override
  protected RuntimeException failEx(String message) {
    return new IllegalStateException(message);
  }

  /**
   * Transparent proxy that automatically adds authentication to each request
   */
  public static class Transparent extends ProxyServlet.Transparent {
    private String _basicAuth;

    @Override
    public void init(ServletConfig config) throws ServletException {
      super.init(config);
      _basicAuth = config.getInitParameter("BasicAuth");
    }

    @Override
    protected void addProxyHeaders(HttpServletRequest clientRequest, org.eclipse.jetty.client.api.Request proxyRequest) {
      proxyRequest.header("Authorization", _basicAuth);
      super.addProxyHeaders(clientRequest, proxyRequest);
    }
  }

  /**
   * Representation of the User-Password pair
   */
  public static class Credentials {
    private static final int GEN_PASSWORD_LENGTH = 16;

    private final String _user;
    private final String _password;

    private Credentials(String _user, String _password) {
      this._user = _user;
      this._password = _password;
    }

    public String toBasicAuth() {
      return "Basic " + B64Code.encode(_user + ":" + _password);
    }

    public String toHashFileEntry() {
      return _user + ": " + Credential.MD5.digest(_password) + "\n";
    }

    public String toDebugString() {
      return "Credentials[_user='" + _user + "', _password='" + _password + "']";
    }

    public static Credentials make(String user, String password) {
      return new Credentials(user, password);
    }

    public static Credentials make(String user) {
      return make(user, SecurityUtils.passwordGenerator(GEN_PASSWORD_LENGTH));
    }
  }

  public class LoginHandler extends HandlerWrapper {

    private final String _loginTarget;
    private final String _errorTarget;
    private final byte[] _loginFormData;

    public LoginHandler(String loginTarget, String errorTarget) {
      _loginTarget = loginTarget;
      _errorTarget = errorTarget;
      try {
        _loginFormData = loadLoginFormResource();
      } catch (IOException e) {
        throw new IllegalStateException("Failed to load the login form!", e);
      }
    }

    private byte[] loadLoginFormResource() throws IOException {
      InputStream loginFormStream = LoginHandler.class.getResourceAsStream("/www/login.html");
      if (loginFormStream == null)
        throw new IllegalStateException("Login form resource is missing.");
      ByteArrayOutputStream baos = new ByteArrayOutputStream();
      IOUtils.copy(loginFormStream, baos);
      return baos.toByteArray();
    }

    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
      if (isLoginTarget(target)) {
        if (isPageRequest(request))
          sendLoginForm(response);
        else
          sendUnauthorizedResponse(response, "Access denied. Please login.");
        baseRequest.setHandled(true);
      } else {
        // not for us, invoke wrapped handler
        super.handle(target, baseRequest, request, response);
      }
    }

    private void sendLoginForm(HttpServletResponse response) throws IOException {
      response.setContentType("text/html");
      response.setContentLength(_loginFormData.length);
      response.setStatus(HttpServletResponse.SC_OK);
      IOUtils.write(_loginFormData, response.getOutputStream());
    }

    private boolean isPageRequest(HttpServletRequest request) {
      String accept = request.getHeader("Accept");
      return (accept != null) && accept.contains("text/html");
    }

    private boolean isLoginTarget(String target) {
      return target.equals(_loginTarget) || target.equals(_errorTarget);
    }
  }


}
