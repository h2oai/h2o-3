package water.webserver.jetty9;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.AbstractHandler;
import org.eclipse.jetty.server.handler.HandlerWrapper;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ScheduledExecutorScheduler;
import org.eclipse.jetty.util.thread.Scheduler;
import water.webserver.iface.H2OHttpConfig;
import water.webserver.iface.H2OHttpView;
import water.webserver.iface.LoginType;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

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
        final HttpConnectionFactory httpConnectionFactory = buildHttpConnectionFactory(isSecured);

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


    private HttpConnectionFactory buildHttpConnectionFactory(boolean isSecured) {
        final String proto = isSecured ? "https" : "http";

        final HttpConfiguration httpConfiguration = new HttpConfiguration();
        httpConfiguration.setSendServerVersion(false);
        httpConfiguration.setRequestHeaderSize(getSysPropInt(proto + ".requestHeaderSize", 32 * 1024));
        httpConfiguration.setResponseHeaderSize(getSysPropInt(proto + ".responseHeaderSize", 32 * 1024));
        httpConfiguration.setOutputBufferSize(getSysPropInt(proto + ".responseBufferSize", httpConfiguration.getOutputBufferSize()));

        return new HttpConnectionFactory(httpConfiguration);
    }

    private static int getSysPropInt(String suffix, int defaultValue) {
        return Integer.getInteger(H2OHttpConfig.SYSTEM_PROP_PREFIX + suffix, defaultValue);
    }

    HandlerWrapper authWrapper(Server jettyServer) {
        if (config.loginType == LoginType.NONE) {
            return jettyServer;
        }

        throw new UnsupportedOperationException(
                "Authentication type '" + config.loginType + "' is not supported by this version of H2O."
        );
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
