package water.webserver.jetty12;


import org.eclipse.jetty.ee8.websocket.api.Session;
import org.eclipse.jetty.ee8.websocket.api.WebSocketListener;
import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeRequest;
import org.eclipse.jetty.ee8.websocket.server.JettyServerUpgradeResponse;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketCreator;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServlet;
import org.eclipse.jetty.ee8.websocket.server.JettyWebSocketServletFactory;
import water.webserver.iface.H2OWebsocketServlet;
import water.webserver.iface.WebsocketConnection;
import water.webserver.iface.WebsocketHandler;

import java.io.IOException;

public class Jetty12WebsocketServlet extends JettyWebSocketServlet {

    private final H2OWebsocketServlet impl;

    public Jetty12WebsocketServlet(H2OWebsocketServlet impl) {
        this.impl = impl;
    }

    static class Jetty12WebsocketConnection implements WebsocketConnection {

        private final Session sess;

        Jetty12WebsocketConnection(Session sess) {
            this.sess = sess;
        }

        @Override
        public void sendMessage(String message) throws IOException {
            sess.getRemote().sendString(message);
        }
    }

    class Jetty12WebsocketHandler implements WebSocketListener {

        private WebsocketHandler handler;
        private Jetty12WebsocketConnection conn;

        @Override
        public void onWebSocketConnect(Session sess) {
            conn = new Jetty12WebsocketConnection(sess);
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
        public void onWebSocketClose(int statusCode, String reason) {
            handler.onClose(conn);
            conn = null;
            handler = null;
        }

        @Override
        public void onWebSocketError(Throwable cause) {
            cause.printStackTrace();
        }
    }

    @Override
    protected void configure(JettyWebSocketServletFactory factory) {
        factory.setCreator(new H2OWebSocketCreator());
    }

    /**
     * Returns a fresh inner-class {@link Jetty12WebsocketHandler} per upgrade so the handler can
     * reference the enclosing servlet's {@link H2OWebsocketServlet} impl. The default
     * {@code DecoratedObjectFactory} only instantiates static 0-arg classes.
     */
    public class H2OWebSocketCreator implements JettyWebSocketCreator {
        @Override
        public Object createWebSocket(JettyServerUpgradeRequest req, JettyServerUpgradeResponse resp) {
            return new Jetty12WebsocketHandler();
        }
    }
}
