package water.webserver.jetty9;

import org.eclipse.jetty.server.HttpConfiguration;

public class H2OHttpConfiguration extends HttpConfiguration {
    private boolean _relativeRedirectAllowed;

    public void setRelativeRedirectAllowed(boolean allowed) {
        _relativeRedirectAllowed = allowed;
    }

    public boolean isRelativeRedirectAllowed() {
        return _relativeRedirectAllowed;
    }
} 
