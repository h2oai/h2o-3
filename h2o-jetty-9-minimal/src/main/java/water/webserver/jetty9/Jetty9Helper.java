package water.webserver.jetty9;

import org.eclipse.jetty.security.*;
import org.eclipse.jetty.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.security.authentication.FormAuthenticator;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.server.session.SessionHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.security.Constraint;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import water.webserver.config.ConnectionConfiguration;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.LoginType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
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

        final Server jettyServer;
        if (config.ensure_daemon_threads) {
            QueuedThreadPool pool = new QueuedThreadPool();
            pool.setDaemon(true);
            jettyServer = new Server(pool);
            // Ensure the threads started by jetty are daemon threads so they don't prevent stopping of H2O
            Scheduler s = jettyServer.getBean(Scheduler.class);
            jettyServer.updateBean(s, new ScheduledExecutorScheduler(null, true));
        } else 
            jettyServer = new Server();

        final boolean isSecured = config.jks != null;
        final HttpConfiguration httpConfiguration = makeHttpConfiguration(new ConnectionConfiguration(isSecured));
        final HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfiguration);

        final ServerConnector connector;
        if (isSecured) {
            final SslContextFactory sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(config.jks);
            sslContextFactory.setKeyStorePassword(config.jks_pass);
            if (config.jks_alias != null) {
                sslContextFactory.setCertAlias(config.jks_alias);
            }
            connector = new ServerConnector(jettyServer, AbstractConnectionFactory.getFactories(sslContextFactory, httpConnectionFactory));
        } else {
            connector = new ServerConnector(jettyServer, httpConnectionFactory);
        }
        if (ip != null) {
            connector.setHost(ip);
        }
        connector.setPort(port);
        jettyServer.setConnectors(new Connector[]{connector});

        return jettyServer;
    }

    static HttpConfiguration makeHttpConfiguration(ConnectionConfiguration cfg) {
        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setRequestHeaderSize(cfg.getRequestHeaderSize());
        httpConfiguration.setResponseHeaderSize(cfg.getResponseHeaderSize());
        httpConfiguration.setOutputBufferSize(cfg.getOutputBufferSize(httpConfiguration.getOutputBufferSize()));
        httpConfiguration.setRelativeRedirectAllowed(cfg.isRelativeRedirectAllowed());
        httpConfiguration.setIdleTimeout(cfg.getIdleTimeout());
        return httpConfiguration;
    }

    HandlerWrapper authWrapper(Server jettyServer) {
        if (config.loginType == LoginType.NONE) {
            return jettyServer;
        }

        final LoginService loginService;
        final Authenticator authenticator;
        switch (config.loginType) {
            case HASH:
                loginService = new HashLoginService("H2O", config.login_conf);
                authenticator = new BasicAuthenticator();
                break;
            case LDAP:
            case KERBEROS:
            case PAM:
            case SPNEGO:
            default:
                throw new UnsupportedOperationException(
                        "Authentication type '" + config.loginType + "' is not supported by this version of H2O."
                );
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
        security.setLoginService(loginService);
        security.setAuthenticator(authenticator);

        final SessionHandler sessionHandler = new SessionHandler();
        if (config.session_timeout > 0) {
            sessionHandler.setMaxInactiveInterval(config.session_timeout * 60);
        }
        sessionHandler.setHandler(security);
        jettyServer.setSessionIdManager(sessionHandler.getSessionIdManager());

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
