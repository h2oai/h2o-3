package water.webserver.jetty9;

import org.eclipse.jetty.jaas.JAASLoginService;
import org.eclipse.jetty.security.Authenticator;
import org.eclipse.jetty.security.ConstraintMapping;
import org.eclipse.jetty.security.ConstraintSecurityHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SpnegoLoginService;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.security.authentication.SpnegoAuthenticator;
import org.eclipse.jetty.server.ConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Request;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.LoginType;

import javax.security.auth.login.AppConfigurationEntry;
import javax.security.auth.login.Configuration;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

class Jetty9Helper {

    private final H2OHttpConfig config;
    private final H2OHttpView h2oHttpView;

    Jetty9Helper(H2OHttpView h2oHttpView) {
        this.h2oHttpView = h2oHttpView;
        this.config = h2oHttpView.getConfig();
    }

    Server createJettyServer(String ip, int port) {
        System.setProperty("org.eclipse.jetty.server.Request.maxFormContentSize", Integer.toString(Integer.MAX_VALUE));

        final Server jettyServer = new Server();

        final ServerConnector connector;
        final String proto;
        if (config.jks != null) {
            proto = "https";
            final SslContextFactory sslContextFactory = new SslContextFactory(config.jks);
            sslContextFactory.setKeyStorePassword(config.jks_pass);

            connector = new ServerConnector(jettyServer, sslContextFactory);
        } else {
            proto = "http";
            connector = new ServerConnector(jettyServer);
        }
        if (ip != null) {
            connector.setHost(ip);
        }
        connector.setPort(port);
        final Collection<ConnectionFactory> connectionFactories = buildConnectionFactory(proto);
        jettyServer.setConnectors(new Connector[]{connector});

        connector.setConnectionFactories(new ArrayList<ConnectionFactory>());
        connector.setConnectionFactories(connectionFactories);
        return jettyServer;
    }


    private Collection<ConnectionFactory> buildConnectionFactory(String proto) {
        final ArrayList<ConnectionFactory> connectionFactories = new ArrayList<>();
        ConnectionFactory connectionFactory = new HttpConnectionFactory();
        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(true);
        httpConfiguration.setRequestHeaderSize(getSysPropInt(proto + ".requestHeaderSize", 32 * 1024));
        httpConfiguration.setResponseHeaderSize(getSysPropInt(proto + ".responseHeaderSize", 32 * 1024));
        httpConfiguration.setOutputBufferSize(getSysPropInt(proto + ".responseBufferSize", httpConfiguration.getOutputBufferSize()));

        connectionFactories.add(connectionFactory);

        return connectionFactories;
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

        constraint.setRoles(new String[]{Constraint.ANY_AUTH});

        final ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*"); // Lock down all API calls
        mapping.setConstraint(constraint);
        security.setConstraintMappings(Collections.singletonList(mapping));

        // Authentication / Authorization
        final Authenticator authenticator;
        if (config.form_auth) {
            FormAuthenticator formAuthenticator = new FormAuthenticator("/login", "/loginError", false);
            authenticator = new Jetty9DelegatingAuthenticator(primaryAuthenticator, formAuthenticator);
        } else {
            authenticator = primaryAuthenticator;
        }
        security.setLoginService(loginService);
        security.setAuthenticator(authenticator);

        final SessionHandler sessionHandler = new SessionHandler();
        if (config.session_timeout > 0) {
            sessionHandler.getSessionManager().setMaxInactiveInterval(config.session_timeout * 60);
        }
        sessionHandler.setHandler(security);
        jettyServer.setSessionIdManager(sessionHandler.getSessionManager().getSessionIdManager());

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

        if (null != config.context_path && !config.context_path.isEmpty()) {
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
