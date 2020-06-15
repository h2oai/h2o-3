package hex.tree.xgboost.remote;

import org.eclipse.jetty.websocket.WebSocket;
import org.eclipse.jetty.websocket.WebSocketServlet;

import javax.servlet.http.HttpServletRequest;

public class RemoteXGBoostWebSocketServlet extends WebSocketServlet {

    @Override
    public WebSocket doWebSocketConnect(
        HttpServletRequest request, String protocol
    ) {
        return null;
    }

}
