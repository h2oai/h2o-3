package hex.steam;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.*;

import static hex.steam.SteamMessenger.TYPE;

public class SteamMessageExchange implements SteamMessageSender {

    private static final Logger LOG = Logger.getLogger(SteamMessageExchange.class);

    private static final Type MAP_TYPE = new TypeToken<Map<String, String>>() {}.getType();

    private final Gson gson = new Gson();
    private final SteamWebsocketServlet servlet;
    private final List<SteamMessenger> messengers;

    SteamMessageExchange(SteamWebsocketServlet servlet) {
        this.servlet = servlet;
        List<SteamMessenger> res = new ArrayList<>();
        for (SteamMessenger l : ServiceLoader.load(SteamMessenger.class)) {
            res.add(l);
        }
        messengers = Collections.unmodifiableList(res);
    }

    public void onConnected() {
        for (SteamMessenger listener : messengers) {
            try {
                listener.onConnectionStateChange(this);
            } catch (Exception e) {
                LOG.error("Error while onConnected event.", e);
            }
        }
    }

    public void onConnectionClosed() {
        for (SteamMessenger listener : messengers) {
            try {
                listener.onConnectionStateChange(null);
            } catch (Exception e) {
                LOG.error("Error while onConnectionClosed event.", e);
            }
        }
    }

    public void distributeMessage(String message) {
        Map<String, String> parsedMessage = Collections.unmodifiableMap(gson.fromJson(message, MAP_TYPE));
        for (SteamMessenger listener : messengers) {
            if (parsedMessage.get(TYPE) != null) {
                try {
                    listener.onMessage(parsedMessage);
                } catch (Exception e) {
                    LOG.error("Error while processing steam message.", e);
                }
            } else {
                LOG.error("Received message from steam without type: " + message);
            }
        }
    }

    @Override
    public void sendMessage(Map<String, String> message) throws IOException {
        servlet.sendMessage(gson.toJson(message));
    }

}
