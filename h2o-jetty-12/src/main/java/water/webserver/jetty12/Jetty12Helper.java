package water.webserver.jetty12;

import ai.h2o.org.eclipse.jetty.security.jaas.H2OJAASLoginService;
import org.eclipse.jetty.ee8.nested.AbstractHandler;
import org.eclipse.jetty.ee8.nested.HandlerWrapper;
import org.eclipse.jetty.ee8.nested.Request;
import org.eclipse.jetty.ee8.nested.ServletConstraint;
import org.eclipse.jetty.ee8.nested.SessionHandler;
import org.eclipse.jetty.ee8.security.Authenticator;
import org.eclipse.jetty.ee8.security.ConstraintMapping;
import org.eclipse.jetty.ee8.security.ConstraintSecurityHandler;
import org.eclipse.jetty.ee8.security.authentication.BasicAuthenticator;
import org.eclipse.jetty.ee8.security.authentication.ConfigurableSpnegoAuthenticator;
import org.eclipse.jetty.ee8.security.authentication.FormAuthenticator;
import org.eclipse.jetty.ee8.servlet.FilterHolder;
import org.eclipse.jetty.ee8.servlet.ServletContextHandler;
import org.eclipse.jetty.ee8.websocket.server.config.JettyWebSocketServletContainerInitializer;
import org.eclipse.jetty.security.DefaultIdentityService;
import org.eclipse.jetty.security.EmptyLoginService;
import org.eclipse.jetty.http.UriCompliance;
import org.eclipse.jetty.security.HashLoginService;
import org.eclipse.jetty.util.resource.ResourceFactory;
import org.eclipse.jetty.security.IdentityService;
import org.eclipse.jetty.security.LoginService;
import org.eclipse.jetty.security.SPNEGOLoginService;
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
import javax.servlet.DispatcherType;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Properties;

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
            // Ensure the threads started by jetty are daemon threads, so they don't prevent stopping of H2O
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
        // Jetty 12 rejects empty path segments (e.g. /3/Frames/) by default; Jetty 9 was lenient.
        // Match the legacy behavior so existing clients that send empty-id URLs still work.
        httpConfiguration.setUriCompliance(UriCompliance.LEGACY);
        return httpConfiguration;
    }

    HandlerWrapper authWrapper(Server jettyServer) {
        if (config.loginType == LoginType.NONE) {
            // Caller expects a HandlerWrapper to set as the context parent; wrap server in a pass-through.
            HandlerWrapper passthrough = new HandlerWrapper();
            // Server itself is a Handler.Wrapper, but we need an ee8.nested wrapper for ServletContextHandler placement.
            // Since no auth is configured, caller will still attach servlet context below; a trivial wrapper is fine.
            return passthrough;
        }

        final LoginService loginService;
        final Authenticator primaryAuthenticator;
        switch (config.loginType) {
            case HASH:
                loginService = new HashLoginService("H2O",
                        ResourceFactory.closeable().newResource(config.login_conf));
                primaryAuthenticator = new BasicAuthenticator();
                break;
            case LDAP:
            case KERBEROS:
            case PAM:
                // Jetty 12 dropped bundled JAAS support — use our in-tree JDK-LoginContext bridge.
                loginService = new H2OJAASLoginService(config.loginType.jaasRealm);
                primaryAuthenticator = new BasicAuthenticator();
                break;
            case SPNEGO:
                System.setProperty("javax.security.auth.useSubjectCredsOnly", "false");
                SPNEGOLoginService spnegoLs = new SPNEGOLoginService(config.loginType.jaasRealm, new EmptyLoginService());
                String targetName = readSpnegoTargetName(config.spnego_properties);
                if (targetName != null) {
                    spnegoLs.setServiceName(targetName);
                }
                loginService = spnegoLs;
                primaryAuthenticator = new ConfigurableSpnegoAuthenticator();
                break;
            default:
                throw new UnsupportedOperationException(config.loginType + "");
        }
        final IdentityService identityService = new DefaultIdentityService();
        loginService.setIdentityService(identityService);
        jettyServer.addBean(loginService);

        // Set a security handler as the first handler in the chain.
        final ConstraintSecurityHandler security = new ConstraintSecurityHandler();

        // Set up a constraint to authenticate all calls, and allow certain roles in.
        final ServletConstraint constraint = new ServletConstraint();
        constraint.setName("auth");
        constraint.setAuthenticate(true);
        constraint.setRoles(new String[]{ServletConstraint.ANY_AUTH});

        final ConstraintMapping mapping = new ConstraintMapping();
        mapping.setPathSpec("/*"); // Lock down all API calls
        mapping.setConstraint(constraint);
        security.setConstraintMappings(Collections.singletonList(mapping));

        final Authenticator authenticator;
        if (config.form_auth) {
            FormAuthenticator formAuthenticator = new FormAuthenticator("/login", "/loginError", false);
            authenticator = new Jetty12DelegatingAuthenticator(primaryAuthenticator, formAuthenticator);
        } else {
            authenticator = primaryAuthenticator;
        }
        security.setLoginService(loginService);
        security.setAuthenticator(authenticator);

        final SessionHandler sessionHandler = new SessionHandler();
        if (config.session_timeout > 0) {
            sessionHandler.setMaxInactiveInterval(config.session_timeout * 60);
        }
        sessionHandler.setHandler(security);

        return security;
    }

    /**
     * Hook up Jetty handlers.  Do this before start() is called.
     */
    ServletContextHandler createServletContextHandler() {
        final ServletContextHandler context = new ServletContextHandler(ServletContextHandler.NO_SECURITY | ServletContextHandler.NO_SESSIONS);
        if (null != config.context_path && !config.context_path.isEmpty()) {
            context.setContextPath(config.context_path);
        } else {
            context.setContextPath("/");
        }
        // Jetty 12 requires explicit WebSocket subsystem registration on the servlet context
        // before any JettyWebSocketServlet can be initialized (otherwise throws
        // IllegalStateException "WebSocketComponents has not been created").
        JettyWebSocketServletContainerInitializer.configure(context, null);
        // Gate filter — initializes request-scoped ThreadLocals and blocks TRACE. In Jetty 9 this
        // was an AbstractHandler above the servlet context; in Jetty 12 Server only accepts core
        // Handlers, so the equivalent logic moves into a servlet Filter registered on the context.
        context.addFilter(new FilterHolder(new H2OGateFilter(h2oHttpView)), "/*",
                EnumSet.of(DispatcherType.REQUEST));
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

    /**
     * Parse {@code targetName=HTTP/host@REALM} from the legacy {@code -spnego_properties} file so
     * the Jetty 9 CLI contract (docs in h2o-docs/src/product/security.rst) survives unchanged.
     * Jetty 12's {@link SPNEGOLoginService} exposes the equivalent via {@link SPNEGOLoginService#setServiceName(String)}.
     */
    private static String readSpnegoTargetName(String spnegoProperties) {
        if (spnegoProperties == null) {
            return null;
        }
        Properties p = new Properties();
        try (java.io.InputStream in = java.nio.file.Files.newInputStream(Paths.get(spnegoProperties))) {
            p.load(in);
        } catch (IOException e) {
            throw new RuntimeException("Failed to read spnego.properties at " + spnegoProperties, e);
        }
        return p.getProperty("targetName");
    }
}
