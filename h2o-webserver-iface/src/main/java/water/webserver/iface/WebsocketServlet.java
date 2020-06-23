package water.webserver.iface;

public interface WebsocketServlet {
    
    WebsocketHandler onConnect(WebsocketConnection conn);

}
