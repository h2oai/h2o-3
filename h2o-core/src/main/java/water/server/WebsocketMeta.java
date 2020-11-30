package water.server;

import water.webserver.iface.H2OWebsocketServlet;

/**
 * Describes how to register a Websocket in H2O Server
 */
public class WebsocketMeta {

    private final String _contextPath;
    private final Class<? extends H2OWebsocketServlet> _servletClass;

    public WebsocketMeta(String contextPath, Class<? extends H2OWebsocketServlet> servletClass) {
        _contextPath = contextPath;
        _servletClass = servletClass;
    }

    public String getContextPath() {
        return _contextPath;
    }

    public Class<? extends H2OWebsocketServlet> getHandlerClass() {
        return _servletClass;
    }
    
}
