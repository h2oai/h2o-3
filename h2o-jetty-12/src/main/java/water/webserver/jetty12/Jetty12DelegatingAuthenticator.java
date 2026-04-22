package water.webserver.jetty12;

import org.eclipse.jetty.ee8.nested.Authentication;
import org.eclipse.jetty.ee8.security.Authenticator;
import org.eclipse.jetty.ee8.security.ServerAuthException;
import org.eclipse.jetty.ee8.security.authentication.FormAuthenticator;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;

/**
 * Dynamically switches between Form-based authentication
 * and Basic Access authentication.
 * The decision is made based on user's "User-Agent". Browser clients will use Form based
 * authentication, all other clients will use basic auth.
 */
class Jetty12DelegatingAuthenticator implements Authenticator {

  private Authenticator _primaryAuth;
  private FormAuthenticator _formAuth;

  Jetty12DelegatingAuthenticator(Authenticator primaryAuth, FormAuthenticator formAuth) {
    _primaryAuth = primaryAuth;
    _formAuth = formAuth;
  }

  @Override
  public void setConfiguration(AuthConfiguration configuration) {
    _primaryAuth.setConfiguration(configuration);
    _formAuth.setConfiguration(configuration);
  }

  @Override
  public String getAuthMethod() {
    return "FORM_PREFERRED";
  }

  @Override
  public void prepareRequest(ServletRequest request) {
    // Do nothing
  }

  @Override
  public Authentication validateRequest(ServletRequest request, ServletResponse response,
                                        boolean mandatory) throws ServerAuthException {
    if (isBrowserAgent((HttpServletRequest) request))
      return _formAuth.validateRequest(request, response, mandatory);
    else
      return _primaryAuth.validateRequest(request, response, mandatory);
  }

  private static boolean isBrowserAgent(HttpServletRequest request) {
    String userAgent = request.getHeader("User-Agent");
    // Covers all modern browsers (Firefox, Chrome, IE, Edge & Opera)
    return (userAgent != null) &&
            (userAgent.startsWith("Mozilla/") || userAgent.startsWith("Opera/"));
  }

  @Override
  public boolean secureResponse(ServletRequest request, ServletResponse response,
                                boolean mandatory, Authentication.User validatedUser) {
    return true; // both BASIC and FORM return true
  }
}
