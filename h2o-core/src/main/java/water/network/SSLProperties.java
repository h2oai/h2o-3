package water.network;

import java.io.File;
import java.util.Properties;

class SSLProperties extends Properties {

    private final File _pathRoot;

    SSLProperties(File pathRoot) {
        _pathRoot = pathRoot;
    }

    SSLProperties() {
        this(null);
    }

    String[] h2o_ssl_enabled_algorithms() {
        String algs = getProperty("h2o_ssl_enabled_algorithms");
        if(null != algs) {
            return algs.split(",");
        }
        return null;
    }

    String h2o_ssl_protocol(String defaultTLS) { return getProperty("h2o_ssl_protocol", defaultTLS); }

    String h2o_ssl_jks_internal() { return expandPath(getProperty("h2o_ssl_jks_internal")); }
    String h2o_ssl_jks_password() { return getProperty("h2o_ssl_jks_password"); }
    String h2o_ssl_jts() {
        String jts = getProperty("h2o_ssl_jts");
        if (jts == null)
            return h2o_ssl_jks_internal();
        return expandPath(jts);
    }
    String h2o_ssl_jts_password() { return getProperty("h2o_ssl_jts_password") != null ? getProperty("h2o_ssl_jts_password") : getProperty("h2o_ssl_jks_password"); }

    String expandPath(String path) {
        if (path == null)
            return null;
        if (_pathRoot == null)
            return path;
        if (new File(path).isAbsolute())
            return path;
        return new File(_pathRoot, path).getAbsolutePath();
    }

    File getPathRoot() {
        return _pathRoot;
    }
    
}
