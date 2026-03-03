package water.webserver.jetty9;


import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.servlet.*;
import water.webserver.iface.H2OWebsocketServlet;
import water.webserver.iface.WebsocketConnection;
import water.webserver.iface.WebsocketHandler;

import java.io.IOException;

public class Jetty9WebsocketServlet extends WebSocketServlet {

    private final H2OWebsocketServlet impl;

    public Jetty9WebsocketServlet(H2OWebsocketServlet impl) {
        this.impl = impl;
    }

    static class Jetty9WebsocketConnection implements WebsocketConnection {

        private final Session sess;

        Jetty9WebsocketConnection(Session sess) {
            this.sess = sess;
        }

        @Override
        public void sendMessage(String message) throws IOException {
            sess.getRemote().sendString(message);
        }
    }

    class Jetty9WebsocketHandler implements WebSocketListener {
        
        private WebsocketHandler handler;
        private Jetty9WebsocketConnection conn;

        @Override
        public void onWebSocketConnect(Session sess) {
            conn = new Jetty9WebsocketConnection(sess);
            handler = impl.onConnect(conn);
        }

        @Override
        public void onWebSocketBinary(byte[] payload, int offset, int len) {
            // ignore
        }

        @Override
        public void onWebSocketText(String message) {
            handler.onMessage(message);
        }

        @Override
        public void onWebSocketClose(int statusCode, String reason)
        {
            handler.onClose(conn);
            conn = null;
            handler = null;
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            cause.printStackTrace();
        }

    }

    /**
     * Please note, each Servlet has it's own instance of WebSocketServletFactory.
     *
     * @param factory Factory object to register socket creator with.
     */
    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.setCreator(new H2OWebSocketCreator());
    }

    /**
     * Custom in-place socket creator, returning new instance of {@link Jetty9WebsocketHandler}m
     * which already contains the proper {@link WebsocketServlet} implementation the request is being delegated to.
     * <p>
     * This is required, as default {@link WebSocketServletFactory} uses {@link org.eclipse.jetty.util.DecoratedObjectFactory}
     * to instantiate {@link WebSocketListener} classes. This class is only able to instantiate static classes with 0-arg constructor,
     * which inner non-static class {@link Jetty9WebsocketHandler} is NOT.
     */
    public class H2OWebSocketCreator implements WebSocketCreator {

        @Override
        public Object createWebSocket(ServletUpgradeRequest req, ServletUpgradeResponse resp) {
            return new Jetty9WebsocketHandler();
        }
    }
}
