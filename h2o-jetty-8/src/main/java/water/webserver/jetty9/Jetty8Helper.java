package water.webserver.jetty9;

import org.eclipse.jetty.plus.jaas.JAASLoginService;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.bio.SocketConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSocketConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.LoginType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Collections;

class Jetty8Helper {

  private final H2OHttpConfig config;
  private final H2OHttpView h2oHttpView;

  Jetty8Helper(H2OHttpView h2oHttpView) {
    this.h2oHttpView = h2oHttpView;
    this.config = h2oHttpView.getConfig();
  }

  Server createJettyServer(String ip, int port) {
    System.setProperty("org.eclipse.jetty.server.Request.maxFormContentSize", Integer.toString(Integer.MAX_VALUE));

    final Server jettyServer = new Server();
    jettyServer.setSendServerVersion(false);

    final Connector connector;
    final String proto;
    if (config.jks != null) {
      proto = "https";
      final SslContextFactory sslContextFactory = new SslContextFactory(config.jks);
      sslContextFactory.setKeyStorePassword(config.jks_pass);
      connector = new SslSocketConnector(sslContextFactory);
    } else {
      proto = "http";
      connector = new SocketConnector();
    }
    if (ip != null) {
      connector.setHost(ip);
    }
    connector.setPort(port);
    configureConnector(proto, connector);
    jettyServer.setConnectors(new Connector[]{connector});
    return jettyServer;
  }

  // Configure connector via properties which we can modify.
  // Also increase request header size and buffer size from default values
  // located in org.eclipse.jetty.http.HttpBuffersImpl
  // see PUBDEV-5939 for details
  private void configureConnector(String proto, Connector connector) {
    connector.setRequestHeaderSize(getSysPropInt(proto+".requestHeaderSize", 32*1024));
    connector.setRequestBufferSize(getSysPropInt(proto+".requestBufferSize", 32*1024));
    connector.setResponseHeaderSize(getSysPropInt(proto+".responseHeaderSize", connector.getResponseHeaderSize()));
    connector.setResponseBufferSize(getSysPropInt(proto+".responseBufferSize", connector.getResponseBufferSize()));
  }

  private static int getSysPropInt(String suffix, int defaultValue) {
    return Integer.getInteger(H2OHttpConfig.SYSTEM_PROP_PREFIX + suffix, defaultValue);
  }

  HandlerWrapper authWrapper(Server jettyServer) {
    if (config.loginType == LoginType.NONE) {
      return jettyServer;
    }

    // REFER TO http://www.eclipse.org/jetty/documentation/9.1.4.v20140401/embedded-examples.html#embedded-secured-hello-handler
    final LoginService loginService;
    switch (config.loginType) {
      case HASH:
        loginService = new HashLoginService("H2O", config.login_conf);
        break;
      case LDAP:
      case KERBEROS:
      case PAM:
        loginService = new JAASLoginService(config.loginType.jaasRealm);
        break;
      default:
        throw new UnsupportedOperationException(config.loginType + ""); // this can never happen
    }
    final IdentityService identityService = new DefaultIdentityService();
    loginService.setIdentityService(identityService);
    jettyServer.addBean(loginService);

    // Set a security handler as the first handler in the chain.
    final ConstraintSecurityHandler security = new ConstraintSecurityHandler();

    // Set up a constraint to authenticate all calls, and allow certain roles in.
    final Constraint constraint = new Constraint();
    constraint.setName("auth");
    constraint.setAuthenticate(true);

    // Configure role stuff (to be disregarded).  We are ignoring roles, and only going off the user name.
    //
    //   Jetty 8 and prior.
    //
    //     Jetty 8 requires the security.setStrict(false) and ANY_ROLE.
    security.setStrict(false);
    constraint.setRoles(new String[]{Constraint.ANY_ROLE});

    //   Jetty 9 and later.
    //
    //     Jetty 9 and later uses a different servlet spec, and ANY_AUTH gives the same behavior
    //     for that API version as ANY_ROLE did previously.  This required some low-level debugging
    //     to figure out, so I'm documenting it here.
    //     Jetty 9 did not require security.setStrict(false).
    //
    // constraint.setRoles(new String[]{Constraint.ANY_AUTH});

    final ConstraintMapping mapping = new ConstraintMapping();
    mapping.setPathSpec("/*"); // Lock down all API calls
    mapping.setConstraint(constraint);
    security.setConstraintMappings(Collections.singletonList(mapping));

    // Authentication / Authorization
    final Authenticator authenticator;
    if (config.form_auth) {
      BasicAuthenticator basicAuthenticator = new BasicAuthenticator();
      FormAuthenticator formAuthenticator = new FormAuthenticator("/login", "/loginError", false);
      authenticator = new Jetty8DelegatingAuthenticator(basicAuthenticator, formAuthenticator);
    } else {
      authenticator = new BasicAuthenticator();
    }
    security.setLoginService(loginService);
    security.setAuthenticator(authenticator);

    final HashSessionIdManager idManager = new HashSessionIdManager();
    jettyServer.setSessionIdManager(idManager);

    final HashSessionManager manager = new HashSessionManager();
    if (config.session_timeout > 0) {
      manager.setMaxInactiveInterval(config.session_timeout * 60);
    }

    final SessionHandler sessionHandler = new SessionHandler(manager);
    sessionHandler.setHandler(security);

    // Pass-through to H2O if authenticated.
    jettyServer.setHandler(sessionHandler);
    return security;

  }

  /**
   * Hook up Jetty handlers.  Do this before start() is called.
   */
  ServletContextHandler createServletContextHandler() {
    // Both security and session handlers are already created (Note: we don't want to create a new separate session
    // handler just for ServletContextHandler - we want to have just one SessionHandler & SessionManager)
    final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);

    if(null != config.context_path && ! config.context_path.isEmpty()) {
      context.setContextPath(config.context_path);
    } else {
      context.setContextPath("/");
    }
    return context;
  }

  Handler authenticationHandler() {
    return new AuthenticationHandler();
  }

  private class AuthenticationHandler extends AbstractHandler {
    @Override
    public void handle(String target, Request baseRequest, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
      boolean handled = h2oHttpView.authenticationHandler(request, response);
      if (handled) {
        baseRequest.setHandled(true);
      }
    }
  }
}
