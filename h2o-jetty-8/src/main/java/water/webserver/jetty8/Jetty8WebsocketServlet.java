package water.webserver.jetty8;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;
import water.webserver.iface.WebsocketConnection;
import water.webserver.iface.WebsocketHandler;
import water.webserver.iface.H2OWebsocketServlet;

import javax.servlet.http.HttpServletRequest;
import java.io.IOException;

public class Jetty8WebsocketServlet extends WebSocketServlet {
    
    private final H2OWebsocketServlet impl;

    public Jetty8WebsocketServlet(H2OWebsocketServlet impl) {
        this.impl = impl;
    }
    
    static class Jetty8WebsocketConnection implements WebsocketConnection {

        private final WebSocket.Connection connection;

        Jetty8WebsocketConnection(WebSocket.Connection connection) {
            this.connection = connection;
        }

        @Override
        public void sendMessage(String message) throws IOException {
            connection.sendMessage(message);
        }
    }

    class Jetty8WebsocketHandler implements WebSocket, WebSocket.OnTextMessage {
        
        private Jetty8WebsocketConnection conn;
        private WebsocketHandler handler;

        @Override
        public void onMessage(String data) {
            handler.onMessage(data);
        }

        @Override
        public void onOpen(Connection connection) {
            conn = new Jetty8WebsocketConnection(connection);
            handler = impl.onConnect(conn);
        }

        @Override
        public void onClose(int closeCode, String message) {
            handler.onClose(conn);
            handler = null;
            conn = null;
        }

    }

    @Override
    public WebSocket doWebSocketConnect(
        HttpServletRequest request, String protocol
    ) {
        return new Jetty8WebsocketHandler();
    }

}
