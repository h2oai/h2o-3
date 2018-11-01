package water;

import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.server.Authentication;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Dynamically switches between Form-based authentication
 * and Basic Access authentication.
 * The decision is made based on user's "User-Agent". Browser clients will use Form based
 * authentication, all other clients will use basic auth.
 */
class DelegatingAuthenticator implements Authenticator {

  private BasicAuthenticator _basicAuth;
  private FormAuthenticator _formAuth;

  DelegatingAuthenticator(BasicAuthenticator basicAuth, FormAuthenticator formAuth) {
    _basicAuth = basicAuth;
    _formAuth = formAuth;
  }

  @Override
  public void setConfiguration(AuthConfiguration configuration) {
    _basicAuth.setConfiguration(configuration);
    _formAuth.setConfiguration(configuration);
  }

  @Override
  public String getAuthMethod() {
    return "FORM_PREFERRED";
  }

  @Override
  public Authentication validateRequest(ServletRequest request, ServletResponse response,
                                        boolean mandatory) throws ServerAuthException {
    if (isBrowserAgent((HttpServletRequest) request))
      return _formAuth.validateRequest(request, response, mandatory);
    else
      return _basicAuth.validateRequest(request, response, mandatory);
  }

  private static boolean isBrowserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    // Covers all modern browsers (Firefox, Chrome, IE, Edge & Opera)
    return (userAgent != null) &&
            (userAgent.startsWith("Mozilla/") || userAgent.startsWith("Opera/"));
  }

  @Override
  public boolean secureResponse(ServletRequest request, ServletResponse response,
                                boolean mandatory, Authentication.User validatedUser) throws ServerAuthException {
    return true; // both BASIC and FORM return true
  }

}
