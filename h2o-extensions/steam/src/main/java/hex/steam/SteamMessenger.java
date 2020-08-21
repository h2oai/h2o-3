package hex.steam;

import java.util.Map;

/**
 * Receives messages from Steam and is notified of connection state changes.
 */
public interface SteamMessenger {

    String ID = "_id";
    String TYPE = "_type";

    /**
     * Called by the servlet when connection state has changed.
     * 
     * @param sender when connected it is set to a SteamMessageSender object which this messenger can use later to
     *               send messages to steam. Once disconnected this method will be called with null to notify the
     *               messenger that steam is not currently connected.
     */
    void onConnectionStateChange(SteamMessageSender sender);

    /**
     * Called for every full message received from steam. All messages are passed to all listeners.
     * 
     * @param message Parsed message from Steam.
     */
    void onMessage(Map<String, String> message);
    
}
