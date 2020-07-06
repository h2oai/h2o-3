package hex.steam;

import org.apache.log4j.Logger;
import water.H2O;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SteamHelloMessenger implements SteamMessenger {

    Logger LOG = Logger.getLogger(SteamHelloMessenger.class);

    private SteamMessageSender sender;
    
    @Override
    public void onConnectionStateChange(SteamMessageSender sender) {
        this.sender = sender;
    }

    @Override
    public void onMessage(Map<String, String> message) {
        if ("hello".equals(message.get(TYPE))) {
            assert sender != null : "Received message but sender is null";
            Map<String, String> response = new HashMap<>();
            response.put(TYPE, "hello_response");
            response.put(ID, message.get(ID) + "_response");
            response.put("version", H2O.ABV.projectVersion());
            response.put("branch", H2O.ABV.branchName());
            response.put("hash", H2O.ABV.lastCommitHash());
            response.put("cloud_size", String.valueOf(H2O.CLOUD.size()));
            try {
                sender.sendMessage(response);
            } catch (IOException e) {
                LOG.error("Failed to send response to hello.", e);
            }
        }
    }

}
