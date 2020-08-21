package hex.steam;

import java.io.IOException;
import java.util.Map;

/**
 * Send messages from H2O to Steam
 */
public interface SteamMessageSender {

    /**
     * Delivers message to Steam
     * 
     * @param message message to be sent
     * @throws IOException when sending of the message fails for some reason
     */
    void sendMessage(Map<String, String> message) throws IOException;

}
