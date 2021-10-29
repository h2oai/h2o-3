package water.webserver.config;

import water.webserver.iface.H2OHttpConfig;

public class ConnectionConfiguration {

    private final String _scheme;

    public ConnectionConfiguration(boolean isSecured) {
        _scheme = isSecured ? "https" : "http";
    }

    public int getRequestHeaderSize() {
        return getSysPropInt( "requestHeaderSize", 32 * 1024);
    }

    public int getRequestBufferSize() {
        return getSysPropInt( "requestBufferSize", 32 * 1024);
    }

    public int getResponseHeaderSize() {
        return getSysPropInt("responseHeaderSize", 32 * 1024);
    }

    public int getOutputBufferSize(int defaultOutputBufferSize) {
        return getSysPropInt("responseBufferSize", defaultOutputBufferSize);
    }

    public boolean isRelativeRedirectAllowed() {
        return getSysPropBool("relativeRedirectAllowed", true);
    }

    private int getSysPropInt(String suffix, int defaultValue) {
        return Integer.parseInt(
                getProperty(H2OHttpConfig.SYSTEM_PROP_PREFIX + _scheme + "." + suffix, String.valueOf(defaultValue))
        );
    }

    private boolean getSysPropBool(String suffix, boolean defaultValue) {
        return Boolean.parseBoolean(
                getProperty(H2OHttpConfig.SYSTEM_PROP_PREFIX + _scheme + "." + suffix, String.valueOf(defaultValue))
        );
    }

    protected String getProperty(String name, String defaultValue) {
        return System.getProperty(name, defaultValue);
    }

}
