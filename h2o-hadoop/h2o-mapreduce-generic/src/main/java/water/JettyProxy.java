package water;

import org.eclipse.jetty.client.HttpExchange;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlets.ProxyServlet;
import org.eclipse.jetty.util.B64Code;
import org.eclipse.jetty.util.security.Credential;
import water.network.SecurityUtils;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;

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
    ServletHolder proxyServlet = new ServletHolder(Transparent.class);
    proxyServlet.setInitParameter("ProxyTo", _proxyTo);
    proxyServlet.setInitParameter("Prefix", "/");
    proxyServlet.setInitParameter("Password", _credentials._password);
    context.addServlet(proxyServlet, "/*");
    handlerWrapper.setHandler(context);
  }

  @Override
  protected RuntimeException failEx(String message) {
    return new IllegalStateException(message);
  }

  /**
   * Transparent proxy that automatically adds authentication to each request
   */
  public static class Transparent extends ProxyServlet.Transparent {
    private String _password;

    @Override
    public void init(ServletConfig config) throws ServletException {
      super.init(config);
      _password = config.getInitParameter("Password");
    }

    @Override
    protected void customizeExchange(HttpExchange exchange, HttpServletRequest request) {
      exchange.setRequestHeader("Authorization", getBasicAuth(request));
    }

    private String getBasicAuth(HttpServletRequest request) {
      // Note: we need to use the authenticated user and pass it to the H2O node
      // H2O will be responsible of authorizing the user (it will reject it if the cluster was started for another user)
      return "Basic " + B64Code.encode(request.getUserPrincipal().getName() + ":" + _password);
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

}
