package water.network;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.*;
import java.util.Properties;

public class SecurityUtils {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String AB = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz";

    private final static String[] keyToolCandidates = new String[]{
            "sun.security.tools.KeyTool",      // Java6
            "sun.security.tools.keytool.Main", // Java7+
            "com.ibm.crypto.tools.KeyTool"     // IBM Java
    };

    private static StoreCredentials generateKeystore(String password, String location) throws Exception {
        return generateKeystore(password, "h2o-internal.jks", location);
    }

    private static StoreCredentials generateKeystore(String password) throws Exception {
        return generateKeystore(password, "h2o-internal.jks", "");
    }

    private static StoreCredentials generateKeystore(String password, String name, String location) throws Exception {
        String path = null != location && !location.isEmpty() ? location + File.separatorChar + name : name;
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

        new File(path).deleteOnExit();

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

        // Unsupported JRE/JDK
        throw new IllegalStateException("Your Java version doesn't support generating keystore. " +
                "Please use Oracle/OpenJDK version 8 or later.");
    }

    public static SSLCredentials generateSSLPair(String passwd, String name, String location) throws Exception {
        StoreCredentials jks = generateKeystore(passwd, name, location);
        return new SSLCredentials(jks, jks);
    }

    public static SSLCredentials generateSSLPair() throws Exception {
        Path temp = Files.createTempDirectory("h2o-internal-jks-" + Long.toString(System.nanoTime()));
        temp.toFile().deleteOnExit();
        StoreCredentials jks = generateKeystore(passwordGenerator(16), temp.toAbsolutePath().toString());
        return new SSLCredentials(jks, jks);
    }

    public static String passwordGenerator(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append(AB.charAt(RANDOM.nextInt(AB.length())));
        }
        return sb.toString();
    }

    public static String generateSSLConfig(SSLCredentials credentials) throws IOException {
        File temp = File.createTempFile("h2o-internal-" + Long.toString(System.nanoTime()), "-ssl.properties");
        temp.deleteOnExit();
        return generateSSLConfig(credentials, temp.getAbsolutePath());
    }

    static String generateSSLConfig(SSLCredentials credentials, String file) throws IOException {
        Properties sslConfig = new Properties();
        sslConfig.put("h2o_ssl_protocol", defaultTLSVersion());
        sslConfig.put("h2o_ssl_jks_internal", credentials.jks.name);
        sslConfig.put("h2o_ssl_jks_password", credentials.jks.pass);
        sslConfig.put("h2o_ssl_jts", credentials.jts.name);
        sslConfig.put("h2o_ssl_jts_password", credentials.jts.pass);
        FileOutputStream output = new FileOutputStream(file);
        try {
            sslConfig.store(output, "");
            output.close();
        } finally {
            try {
                output.close();
            } catch (IOException e) {
                // ignore
            }
        }
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
            return null != path && !path.isEmpty() ? path + File.separatorChar + name : name;
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
