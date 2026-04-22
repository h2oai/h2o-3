package water.webserver.jetty12;

import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.HandlerWrapper;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.nested.ServletConstraint;
import org.eclipse.jetty.ee8.nested.SessionHandler;
import org.eclipse.jetty.ee8.security.Authenticator;
import org.eclipse.jetty.ee8.security.ConstraintMapping;
import org.eclipse.jetty.ee8.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee8.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.server.AbstractConnectionFactory;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.HttpConfiguration;
import org.eclipse.jetty.server.HttpConnectionFactory;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
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

class Jetty12Helper {

    private final H2OHttpConfig config;
    private final H2OHttpView h2oHttpView;

    Jetty12Helper(H2OHttpView h2oHttpView) {
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
            Scheduler s = jettyServer.getBean(Scheduler.class);
            jettyServer.updateBean(s, new ScheduledExecutorScheduler(null, true));
        } else {
            jettyServer = new Server();
        }

        final boolean isSecured = config.jks != null;
        final HttpConfiguration httpConfiguration = makeHttpConfiguration(new ConnectionConfiguration(isSecured));
        final HttpConnectionFactory httpConnectionFactory = new HttpConnectionFactory(httpConfiguration);

        final ServerConnector connector;
        if (isSecured) {
            final SslContextFactory.Server sslContextFactory = new SslContextFactory.Server();
            sslContextFactory.setKeyStorePath(config.jks);
            sslContextFactory.setKeyStorePassword(config.jks_pass);
            if (config.jks_alias != null) {
                sslContextFactory.setCertAlias(config.jks_alias);
            }
            connector = new ServerConnector(jettyServer, AbstractConnectionFactory.getFactories(sslContextFactory, httpConnectionFactory));
        } else {
            connector = new ServerConnector(jettyServer, httpConnectionFactory);
        }
        connector.setIdleTimeout(httpConfiguration.getIdleTimeout()); // for websockets,...
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
            return new HandlerWrapper();
        }

        final LoginService loginService;
        final Authenticator authenticator;
        switch (config.loginType) {
            case HASH:
                loginService = new HashLoginService("H2O",
                        ResourceFactory.closeable().newResource(config.login_conf));
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

        final ConstraintSecurityHandler security = new ConstraintSecurityHandler();

        final ServletConstraint constraint = new ServletConstraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[]{ServletConstraint.ANY_AUTH});

        final ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*");
        mapping.setConstraint(constraint);
        security.setConstraintMappings(Collections.singletonList(mapping));

        security.setLoginService(loginService);
        security.setAuthenticator(authenticator);

        final SessionHandler sessionHandler = new SessionHandler();
        if (config.session_timeout > 0) {
            sessionHandler.setMaxInactiveInterval(config.session_timeout * 60);
        }
        sessionHandler.setHandler(security);
        return security;
    }

    ServletContextHandler createServletContextHandler() {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        if (null != config.context_path && !config.context_path.isEmpty()) {
            context.setContextPath(config.context_path);
        } else {
            context.setContextPath("/");
        }
        return context;
    }

    org.eclipse.jetty.ee8.nested.Handler authenticationHandler() {
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
