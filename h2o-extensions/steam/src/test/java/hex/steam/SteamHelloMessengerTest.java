package hex.steam;

import org.junit.Test;
import org.junit.runner.RunWith;
import water.H2O;
import water.runner.CloudSize;
import water.runner.H2ORunner;
import water.test.WebsocketClient;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertEquals;

@RunWith(H2ORunner.class)
@CloudSize(1)
public class SteamHelloMessengerTest {

    @Test
    public void testHelloMessageAcknowledgement() throws Exception {
        final WebsocketClient steam = new WebsocketClient();
        try {
            Map<String, String> helloMsg = new HashMap<>();
            helloMsg.put("_type", "hello");
            helloMsg.put("_id", "123456");
            steam.sendMessage(helloMsg);
            System.out.println("Hello message sent.");
            Map<String, String> response = steam.waitToReceiveMessage("hello response");
            assertEquals("hello_response", response.get("_type"));
            assertEquals("123456_response", response.get("_id"));
            assertEquals(H2O.ABV.projectVersion(), response.get("version"));
            assertEquals(H2O.ABV.branchName(), response.get("branch"));
            assertEquals(H2O.ABV.lastCommitHash(), response.get("hash"));
            assertEquals(String.valueOf(H2O.CLOUD.size()), response.get("cloud_size"));
        } finally {
            steam.close();
        }
    }

}
