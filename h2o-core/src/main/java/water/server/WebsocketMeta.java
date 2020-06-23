package water.server;

import water.webserver.iface.WebsocketServlet;

/**
 * Describes how to register a Websocket in H2O Server
 */
public class WebsocketMeta {

    private final String _contextPath;
    private final Class<? extends WebsocketServlet> _servletClass;

    public WebsocketMeta(String contextPath, Class<? extends WebsocketServlet> servletClass) {
        _contextPath = contextPath;
        _servletClass = servletClass;
    }

    public String getContextPath() {
        return _contextPath;
    }

    public Class<? extends WebsocketServlet> getHandlerClass() {
        return _servletClass;
    }
    
}
