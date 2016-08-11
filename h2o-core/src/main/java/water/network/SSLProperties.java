package water.network;

import java.util.Properties;

class SSLProperties extends Properties {

    String[] h2o_ssl_enabled_algorithms() {
        String algs = getProperty("h2o_ssl_enabled_algorithms");
        if(null != algs) {
            return algs.split(",");
        }
        return null;
    }

    String h2o_ssl_protocol() { return getProperty("h2o_ssl_protocol", "TLSv1.2"); }

    String h2o_ssl_jks_internal() { return getProperty("h2o_ssl_jks_internal"); }
    String h2o_ssl_jks_password() { return getProperty("h2o_ssl_jks_password"); }
    String h2o_ssl_jts() { return getProperty("h2o_ssl_jts") != null ? getProperty("h2o_ssl_jts") : getProperty("h2o_ssl_jks_internal"); }
    String h2o_ssl_jts_password() { return getProperty("h2o_ssl_jts_password") != null ? getProperty("h2o_ssl_jts_password") : getProperty("h2o_ssl_jks_password"); }

}
