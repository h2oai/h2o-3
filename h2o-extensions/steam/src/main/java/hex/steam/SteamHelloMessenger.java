package hex.steam;

import java.util.Map;

import static hex.steam.SteamMessageSender.ACK;

public class SteamHelloMessenger implements SteamMessenger {
    
    private SteamMessageSender sender;
    
    @Override
    public void onConnectionStateChange(SteamMessageSender sender) {
        this.sender = sender;
    }

    @Override
    public void onMessage(Map<String, String> message) {
        if (message.get(TYPE).equals("hello")) {
            assert sender != null : "Received message but sender is null";
            ACK(message, sender);
        }
    }

}
