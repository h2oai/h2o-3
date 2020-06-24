package water.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.glassfish.tyrus.client.ClientManager;
import water.H2O;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;

import static org.junit.Assert.fail;

public class WebsocketClient extends Endpoint {

    private Map<String, String> receivedMessage;
    private Session sess;

    private final Gson gson = new Gson();
    
    public WebsocketClient() throws URISyntaxException, IOException, DeploymentException {
        String destUri = "ws://" + H2O.getIpPortString() + "/3/Steam.websocket";
        ClientManager client = ClientManager.createClient();
        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        sess = client.connectToServer(this, cec, new URI(destUri));
    }
    
    public void close() throws IOException {
        sess.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Test Done"));
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config) {
        session.addMessageHandler(String.class, message -> {
            System.out.println("Received message: " + message);
            synchronized (this) {
                receivedMessage = gson.fromJson(message, new TypeToken<Map<String, String>>() {}.getType());
                this.notifyAll();
            }
        });
    }
    
    public void sendMessage(Object msg) throws IOException {
        sess.getBasicRemote().sendText(gson.toJson(msg));
    }

    public Map<String, String> waitToReceiveMessage(String message) {
        return waitToReceiveMessage(message, 10_000);
    }
    
    public Map<String, String> waitToReceiveMessage(String message, int timeout) {
        return waitToReceiveMessage(message, timeout, true);
    }

    public synchronized Map<String, String> waitToReceiveMessage(String message, int timeout, boolean failOnNone) {
        try {
            this.wait(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (receivedMessage != null) {
            Map<String, String> res = receivedMessage;
            receivedMessage = null;
            return res;
        } else if (failOnNone) {
            fail("Expected " + message + ", but no message received from H2O.");
        }
        return null;
    }
}
