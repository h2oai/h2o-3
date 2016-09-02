package water;


import org.eclipse.jetty.security.ServerAuthException;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.Authentication;
import water.util.Log;

import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import java.io.IOException;
import java.net.*;

public class SetAuthenticatorTest {
  /**
   * standalone test demonstrating what one might want to do with a custom Authenticator.
   */
  /*@Test*/ public void testSetAuthenticator() {
    JettyHTTPD.setAuthenticator(new BasicAuthenticator() {  // barely override the default as a for instance
      @Override public Authentication validateRequest(ServletRequest req, ServletResponse res, boolean mandatory) throws ServerAuthException {
        Authentication auth = super.validateRequest(req, res, mandatory);
        Log.info("User Auth:" + auth);
        return auth;
      }
    });
    water.H2OStarter.start(new String[]{"-name", "privateSpecialCloud", "-hash_login", "-login_conf", "scripts/hash_login.conf"}, System.getProperty("user.dir"));
    makeRESTCall();
    H2O.shutdown(0);
  }

  private void makeRESTCall() {
    HttpURLConnection c;
    Authenticator.setDefault(new Authenticator() {
      @Override protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication("user", "h2oh2o".toCharArray());
      }
    });
    try {
      URL url = new URL("http://localhost:54321");
      url.openStream();
      c = (HttpURLConnection)url.openConnection();
      c.getInputStream();

    } catch (ProtocolException e) {
      e.printStackTrace();
    } catch (MalformedURLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
}
