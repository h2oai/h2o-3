package hex.steam;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.glassfish.tyrus.client.ClientManager;
import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.runner.CloudSize;
import water.runner.H2ORunner;

import javax.websocket.*;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamHelloMessengerTest {

    class Steam extends Endpoint {
        
        Map<String, String> receivedMessage;
        
        private final Gson gson = new Gson();
        
        @OnOpen
        public void onOpen(Session session, EndpointConfig config) {
            try {
                session.addMessageHandler(String.class, message -> {
                    System.out.println("Received message: " + message);
                    receivedMessage = gson.fromJson(message, new TypeToken<Map<String, String>>() {}.getType());
                    synchronized (this) { this.notifyAll(); }
                });
                Map<String, String> helloMsg = new HashMap<>();
                helloMsg.put("_type", "hello");
                helloMsg.put("_id", "123");
                session.getBasicRemote().sendText(gson.toJson(helloMsg));
                System.out.println("Hello message sent.");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @SuppressWarnings("SynchronizationOnLocalVariableOrMethodParameter")
    @Test
    public void testHelloMessageAcknowledgement() throws Exception {
        String destUri = "ws://" + H2O.getIpPortString() + "/3/Steam.websocket";
        ClientManager client = ClientManager.createClient();
        final Steam steam = new Steam();
        ClientEndpointConfig cec = ClientEndpointConfig.Builder.create().build();
        Session sess = client.connectToServer(steam, cec, new URI(destUri));
        synchronized (steam) {
            steam.wait(5000);
        }
        assertNotNull("Did not receive response from H2O", steam.receivedMessage);
        assertEquals("ack", steam.receivedMessage.get("_type"));
        assertEquals("123", steam.receivedMessage.get("_id"));
        sess.close(new CloseReason(CloseReason.CloseCodes.NORMAL_CLOSURE, "Test Done"));
    }

}
