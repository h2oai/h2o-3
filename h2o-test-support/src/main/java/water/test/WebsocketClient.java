package water.test;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.log4j.Logger;
import org.glassfish.tyrus.client.ClientManager;
import water.H2O;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Optional;

import static org.junit.Assert.fail;

public class WebsocketClient extends Endpoint {
    
    private static final Logger LOG = Logger.getLogger(WebsocketClient.class);

    private final Session sess;
    private final Gson gson = new Gson();

    private Map<String, String> receivedMessage;
    private String overflowMessage;
    
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
            LOG.info("Received message from H2O: " + message);
            synchronized (this) {
                if (receivedMessage != null) {
                    LOG.info("Received message not stored as last message was not picked up yet.");
                    overflowMessage = message;
                } else {
                    receivedMessage = gson.fromJson(message, new TypeToken<Map<String, String>>() {}.getType());
                }
                this.notifyAll();
            }
        });
    }
    
    public void sendMessage(Object msg) throws IOException {
        final String msgStr = gson.toJson(msg);
        LOG.info("Sending message to H2O: " + msgStr);
        sess.getBasicRemote().sendText(msgStr);
    }

    public Map<String, String> waitToReceiveMessage(String message) {
        return waitToReceiveMessage(message, 10_000);
    }
    
    public Map<String, String> waitToReceiveMessage(String message, int timeoutMillis) {
        return waitToReceiveMessage(message, timeoutMillis, true).get();
    }

    public synchronized Optional<Map<String, String>> waitToReceiveMessage(String message, int timeoutMillis, boolean failOnNone) {
        if (overflowMessage != null) {
            fail("Message received but not handled: " + overflowMessage);
        }
        try {
            this.wait(timeoutMillis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (receivedMessage != null) {
            Map<String, String> res = receivedMessage;
            receivedMessage = null;
            return Optional.of(res);
        } else if (failOnNone) {
            fail("Expected " + message + ", but no message received from H2O.");
        }
        return Optional.empty();
    }
}
