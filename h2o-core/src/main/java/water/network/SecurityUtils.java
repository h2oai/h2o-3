package water.network;

import sun.security.x509.X500Name;

import java.io.*;
import java.security.*;
import java.security.cert.X509Certificate;
import java.util.Properties;

public class SecurityUtils {

    private static SecureRandom RANDOM = new SecureRandom();
    private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";


    private static StoreCredentials generateKeystore(String password) throws Exception {
        try(FileOutputStream fos = new FileOutputStream("h2o-internal.jks")) {
            KeyStore ks = KeyStore.getInstance("JKS");
            ks.load(null, null);

            CertAndKeyGenWrapper keypair = new CertAndKeyGenWrapper("RSA", "SHA1WithRSA", null);
            keypair.generate(1024);
            X509Certificate[] chain = new X509Certificate[1];
            X500Name x500Name = new X500Name("", "", "", "", "", "");
            chain[0] = keypair.getSelfCertificate(x500Name, 365*24L*60L*60L);

            ks.setKeyEntry("h2o-internal", keypair.getPrivateKey(), password.toCharArray(), chain);

            ks.store(fos, password.toCharArray());
            return new StoreCredentials("h2o-internal.jks", "h2o-internal.jks", password);
        }
    }

    public static SSLCredentials generateSSLPair() throws Exception {
        StoreCredentials jks = generateKeystore(passwordGenerator(16));
        return new SSLCredentials(jks, jks);
    }

    private static String passwordGenerator(int len) {
        StringBuilder sb = new StringBuilder(len);
        for( int i = 0; i < len; i++ ) {
            sb.append( AB.charAt( RANDOM.nextInt(AB.length()) ) );
        }
        return sb.toString();
    }

    public static String generateSSLConfig(SSLCredentials credentials) throws IOException {
        Properties sslConfig = new Properties();
        sslConfig.put("h2o_ssl_protocol", "TLSv1.2");
        sslConfig.put("h2o_ssl_jks_internal", credentials.jks.path);
        sslConfig.put("h2o_ssl_jks_password", credentials.jks.pass);
        sslConfig.put("h2o_ssl_jts", credentials.jts.path);
        sslConfig.put("h2o_ssl_jts_password", credentials.jts.pass);
        FileOutputStream output = new FileOutputStream("ssl.properties");
        sslConfig.store(output, "");
        return "ssl.properties";
    }

    public static class StoreCredentials {
        public String name = null;
        public String path = null;
        public String pass = null;

        StoreCredentials(String name, String path, String pass) {
            this.name = name;
            this.path = path;
            this.pass = pass;
        }
    }

    public static class SSLCredentials {
        public StoreCredentials jks;
        public StoreCredentials jts;

        SSLCredentials(StoreCredentials jks, StoreCredentials jts) {
            this.jks = jks;
            this.jts = jts;
        }
    }
}
