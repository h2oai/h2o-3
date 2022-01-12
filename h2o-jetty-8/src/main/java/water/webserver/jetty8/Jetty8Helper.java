package water.webserver.jetty8;

import ai.h2o.org.eclipse.jetty.security.authentication.SpnegoAuthenticator;
import org.eclipse.jetty.plus.jaas.JAASLoginService;
import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.server.session.HashSessionIdManager;
import org.eclipse.jetty.server.session.HashSessionManager;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.server.ssl.SslSelectChannelConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import water.webserver.config.ConnectionConfiguration;
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
    if (config.ensure_daemon_threads) {
      QueuedThreadPool pool = new QueuedThreadPool();
      pool.setDaemon(true);
      jettyServer.setThreadPool(pool);
    }
    jettyServer.setSendServerVersion(false);

    final Connector connector;
    final ConnectionConfiguration connConfig = getConnectionConfiguration();
    if (connConfig.isSecure()) {
      final SslContextFactory sslContextFactory = new SslContextFactory(config.jks);
      sslContextFactory.setKeyStorePassword(config.jks_pass);
      if (config.jks_alias != null) {
        sslContextFactory.setCertAlias(config.jks_alias);
      }
      connector = new SslSelectChannelConnector(sslContextFactory);
    } else {
      connector = new SelectChannelConnector();
    }
    if (ip != null) {
      connector.setHost(ip);
    }
    connector.setPort(port);
    configureConnector(connector, connConfig);
    jettyServer.setConnectors(new Connector[]{connector});
    return jettyServer;
  }

  ConnectionConfiguration getConnectionConfiguration() {
    return new ConnectionConfiguration(config.jks != null);
  }
  
  // Configure connector via properties which we can modify.
  // Also increase request header size and buffer size from default values
  // located in org.eclipse.jetty.http.HttpBuffersImpl
  // see PUBDEV-5939 for details
  static void configureConnector(Connector connector, ConnectionConfiguration cfg) {
    connector.setRequestHeaderSize(cfg.getRequestHeaderSize());
    connector.setRequestBufferSize(cfg.getRequestBufferSize());
    connector.setResponseHeaderSize(cfg.getResponseHeaderSize());
    connector.setResponseBufferSize(cfg.getOutputBufferSize(connector.getResponseBufferSize()));
    if (!cfg.isRelativeRedirectAllowed()) {
      // trick: the default value is enabled -> we need to touch the field only if the relative-redirects are disabled
      //        this means we don't have to worry about deployments where someone substituted our implementation for
      //        something else at assembly time
      Response.RELATIVE_REDIRECT_ALLOWED = false;
    }
    connector.setMaxIdleTime(cfg.getIdleTimeout());
  }

  HandlerWrapper authWrapper(Server jettyServer) {
    if (config.loginType == LoginType.NONE) {
      return jettyServer;
    }

    // REFER TO http://www.eclipse.org/jetty/documentation/9.1.4.v20140401/embedded-examples.html#embedded-secured-hello-handler
    final LoginService loginService;
    final Authenticator primaryAuthenticator;
    switch (config.loginType) {
      case HASH:
        loginService = new HashLoginService("H2O", config.login_conf);
        primaryAuthenticator = new BasicAuthenticator();
        break;
      case LDAP:
      case KERBEROS:
      case PAM:
        loginService = new JAASLoginService(config.loginType.jaasRealm);
        primaryAuthenticator = new BasicAuthenticator();
        break;
      case SPNEGO:
        System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
        loginService = new SpnegoLoginService(config.loginType.jaasRealm, config.spnego_properties);
        primaryAuthenticator = new SpnegoAuthenticator();
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
      final ConnectionConfiguration connConfig = getConnectionConfiguration();
      final Authenticator formAuthenticator = makeFormAuthenticator(connConfig.isRelativeRedirectAllowed());
      authenticator = new Jetty8DelegatingAuthenticator(primaryAuthenticator, formAuthenticator);
    } else {
      authenticator = primaryAuthenticator;
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

  static Authenticator makeFormAuthenticator(boolean useRelativeRedirects) {
    final Authenticator formAuthenticator;
    if (useRelativeRedirects) {
      // If relative redirects are enabled - use our custom modified Authenticator
      formAuthenticator = new water.webserver.jetty8.security.FormAuthenticator(
              "/login", "/loginError", false, true
      );
    } else {
      // Otherwise - prefer the default jetty authenticator 
      formAuthenticator = new org.eclipse.jetty.security.authentication.FormAuthenticator(
              "/login", "/loginError", false
      );
    }
    return formAuthenticator;
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
