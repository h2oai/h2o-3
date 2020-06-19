package hex.steam;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static hex.steam.SteamMessenger.ID;
import static hex.steam.SteamMessenger.TYPE;

public interface SteamMessageSender {

    Logger LOG = Logger.getLogger(SteamMessageSender.class);

    /**
     * Delivers message to Steam
     * 
     * @param message message to be sent
     * @throws IOException when sending of the message fails for some reason
     */
    void sendMessage(Map<String, String> message) throws IOException;

    /**
     * Helper method to send acknowledgement messages to steam
     * @param message message received from steam to acknowledge
     * @param sender sender to deliver the message
     */
    static void ACK(Map<String, String> message, SteamMessageSender sender) {
        Map<String, String> response = new HashMap<>();
        response.put(TYPE, "ack");
        response.put(ID, message.get(ID));
        try {
            sender.sendMessage(response);
        } catch (IOException e) {
            LOG.warn("Failed to send ACK for " + message.get(TYPE), e);
        }
    }

}
