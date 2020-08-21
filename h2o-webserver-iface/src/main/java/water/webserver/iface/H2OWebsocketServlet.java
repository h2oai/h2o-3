package water.webserver.iface;

public interface H2OWebsocketServlet {
    
    WebsocketHandler onConnect(WebsocketConnection conn);

}
