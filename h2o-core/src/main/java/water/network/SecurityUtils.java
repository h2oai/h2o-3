package water.network;

import water.util.Log;

import java.io.*;
import java.security.*;
import java.util.Properties;

public class SecurityUtils {

    private static SecureRandom RANDOM = new SecureRandom();
    private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final static String[] keyToolCandidates = new String[]{
            "sun.security.tools.KeyTool",      // Java6
            "sun.security.tools.keytool.Main", // Java7+
            "com.ibm.crypto.tools.KeyTool"     // IBM Java
    };

    private static StoreCredentials generateKeystore(String password) throws Exception {
        return generateKeystore(password, "h2o-internal.jks", "");
    }

    private static StoreCredentials generateKeystore(String password, String name, String location) throws Exception {
        String path = null != location && !location.isEmpty() ? location + File.pathSeparator + name : name;
        if(new File(path).exists()) {
            throw new IllegalStateException("A file under the location " + path + " already exists. Please delete it first.");
        }

        String[] genKeyArgs = new String[]{
                "-genkeypair",
                "-alias", "h2o-internal",
                "-keyalg", "RSA",
                "-sigalg", "SHA256withRSA",
                "-dname", "CN=Java",
                "-storetype", "JKS",
                "-keypass", password,
                "-keystore", path,
                "-storepass", password,
                "-validity", "3650"
        };

        Class<?> keytool = getKeyToolClass();

        keytool.getMethod("main", String[].class).invoke(null, (Object) genKeyArgs);

        return new StoreCredentials(name, location, password);
    }

    private static Class<?> getKeyToolClass() {
        for (String keyToolCandidate : keyToolCandidates) {
            try {
                return Class.forName(keyToolCandidate);
            } catch (Exception e) {
                // Ignore, try other candidates
            }
        }

        // Unsuported JRE/JDK
        String errorMsg = "This version of Java is not supported. Please use Oracle/OpenJDK/IBM JDK version 6/7/8.";
        Log.err(errorMsg);
        throw new IllegalStateException(errorMsg);
    }

    public static SSLCredentials generateSSLPair(String passwd, String name, String location) throws Exception {
        StoreCredentials jks = generateKeystore(passwd, name, location);
        return new SSLCredentials(jks, jks);
    }

    public static SSLCredentials generateSSLPair() throws Exception {
        StoreCredentials jks = generateKeystore(passwordGenerator(16));
        return new SSLCredentials(jks, jks);
    }

    private static String passwordGenerator(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(RANDOM.nextInt(AB.length())));
        }
        return sb.toString();
    }

    public static String generateSSLConfig(SSLCredentials credentials) throws IOException {
        return generateSSLConfig(credentials, "ssl.properties");
    }

    public static String generateSSLConfig(SSLCredentials credentials, String file) throws IOException {
        Properties sslConfig = new Properties();
        sslConfig.put("h2o_ssl_protocol", defaultTLSVersion());
        sslConfig.put("h2o_ssl_jks_internal", credentials.jks.getLocation());
        sslConfig.put("h2o_ssl_jks_password", credentials.jks.pass);
        sslConfig.put("h2o_ssl_jts", credentials.jts.getLocation());
        sslConfig.put("h2o_ssl_jts_password", credentials.jts.pass);
        FileOutputStream output = new FileOutputStream(file);
        sslConfig.store(output, "");
        return file;
    }

    public static String defaultTLSVersion() {
        return System.getProperty("java.version", "NA").startsWith("1.6") ? "TLSv1" : "TLSv1.2";
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

        public String getLocation() {
            return null != path && !path.isEmpty() ? path + File.pathSeparator + name : name;
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
