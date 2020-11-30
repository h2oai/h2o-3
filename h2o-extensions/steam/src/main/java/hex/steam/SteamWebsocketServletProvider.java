package hex.steam;

import water.server.ServletMeta;
import water.server.ServletProvider;
import water.server.WebsocketMeta;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class SteamWebsocketServletProvider implements ServletProvider {

    private static final List<WebsocketMeta> SERVLETS = Collections.unmodifiableList(Arrays.asList(
        new WebsocketMeta("/3/Steam.websocket", SteamWebsocketServlet.class)
    ));

    @Override
    public List<ServletMeta> servlets() {
        return Collections.emptyList();
    }

    @Override
    public List<WebsocketMeta> websockets() {
        return SERVLETS;
    }

    @Override
    public int priority() {
        return 0;
    }

}
