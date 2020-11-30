package water.webserver.iface;

public interface WebsocketHandler {

    void onClose(WebsocketConnection connection);

    void onMessage(String message);

}
