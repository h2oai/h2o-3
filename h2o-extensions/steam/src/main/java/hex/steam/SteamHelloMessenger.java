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
        if (message.get(TYPE).equals("hello")) {
            assert sender != null : "Received message but sender is null";
            Map<String, String> response = new HashMap<>();
            response.put(TYPE, "cloud_info");
            response.put(ID, message.get(ID) + "_response");
            response.put("cloud_size", String.valueOf(H2O.CLOUD.size()));
            for (int i = 0; i < H2O.CLOUD.size(); i++) {
                response.put("node_address_" + i, H2O.CLOUD.members()[i].getIpPortString());
            }
            try {
                sender.sendMessage(response);
            } catch (IOException e) {
                LOG.error("Failed to send response to hello.", e);
            }
        }
    }

}
