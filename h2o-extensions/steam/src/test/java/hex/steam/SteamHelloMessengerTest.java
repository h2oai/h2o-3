package hex.steam;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.WebsocketClient;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamHelloMessengerTest {

    @Test
    public void testHelloMessageAcknowledgement() throws Exception {
        final WebsocketClient steam = new WebsocketClient();
        try {
            Map<String, String> helloMsg = new HashMap<>();
            helloMsg.put("_type", "hello");
            helloMsg.put("_id", "123");
            steam.sendMessage(helloMsg);
            System.out.println("Hello message sent.");
            Map<String, String> response = steam.waitToReceiveMessage("hello response");
            assertEquals("cloud_info", response.get("_type"));
            assertEquals("123_response", response.get("_id"));
            assertNotNull(response.get("node_address_0"));
        } finally {
            steam.close();
            
        }
    }

}
