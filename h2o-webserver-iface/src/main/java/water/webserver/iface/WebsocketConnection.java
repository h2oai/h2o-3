package water.webserver.iface;

import java.io.IOException;

public interface WebsocketConnection {
    
    void sendMessage(String message) throws IOException;
    
}
