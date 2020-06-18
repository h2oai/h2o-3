package water.webserver.jetty9;


import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.WebSocketListener;
import org.eclipse.jetty.websocket.servlet.WebSocketServlet;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import water.webserver.iface.WebsocketConnection;
import water.webserver.iface.WebsocketHandler;
import water.webserver.iface.WebsocketServlet;

import java.io.IOException;

public class Jetty9WebsocketServlet extends WebSocketServlet {

    private final WebsocketServlet impl;

    public Jetty9WebsocketServlet(WebsocketServlet impl) {
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
    
    @Override
    public void configure(WebSocketServletFactory factory) {
        factory.register(Jetty9WebsocketHandler.class);
    }
}
