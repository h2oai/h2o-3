package hex.steam;

import water.webserver.iface.WebsocketConnection;
import water.webserver.iface.WebsocketHandler;
import water.webserver.iface.H2OWebsocketServlet;

import java.io.IOException;

public class SteamWebsocketServlet implements H2OWebsocketServlet {
    
    private static final Object CONNECTION_LOCK = new Object();

    private WebsocketConnection currentConnection;
    private final SteamMessageExchange exchange;
    
    public SteamWebsocketServlet() {
        exchange = new SteamMessageExchange(this);
    }
    
    private class Handler implements WebsocketHandler {

        @Override
        public void onClose(WebsocketConnection connection) {
            synchronized (CONNECTION_LOCK) {
                if (currentConnection == connection) {
                    currentConnection = null;
                    SteamWebsocketServlet.this.exchange.onConnectionClosed();
                }
            }
        }

        @Override
        public void onMessage(String message) {
            SteamWebsocketServlet.this.exchange.distributeMessage(message);
        }
    }
    
    @Override
    public WebsocketHandler onConnect(WebsocketConnection conn) {
        synchronized (CONNECTION_LOCK) {
            currentConnection = conn;
            SteamWebsocketServlet.this.exchange.onConnected();
        }
        return new Handler();
    }
    
    public void sendMessage(String message) throws IOException {
        WebsocketConnection conn;
        synchronized (CONNECTION_LOCK) {
            conn = currentConnection;
        }
        if (conn == null) {
            throw new IOException("Connection currently not available");
        } else {
            conn.sendMessage(message);
        }
    }

}
