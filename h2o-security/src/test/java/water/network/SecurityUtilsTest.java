package water.network;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import water.network.util.ExternalKeytool;
import water.network.util.JavaVersionUtils;

import java.io.File;
import java.io.FileInputStream;
import java.util.Properties;

import static org.junit.Assert.*;

public class SecurityUtilsTest {

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();
    
    @Test
    public void shouldGenerateKeystoreAndConfig() throws Exception {
        try {
            String location = tmp.newFolder("ssl").getAbsolutePath();
            SecurityUtils.SSLCredentials testKeystore = SecurityUtils.generateSSLPair("test123", "h2o-keystore-test.jks", location);
            String configPath = SecurityUtils.generateSSLConfig(testKeystore, "test-ssl.properties");

            assertTrue(new File(testKeystore.jks.getLocation()).exists());

            Properties sslConfig = new Properties();
            sslConfig.load(new FileInputStream(configPath));
            assertEquals(SecurityUtils.defaultTLSVersion(), sslConfig.getProperty("h2o_ssl_protocol"));
            assertEquals("h2o-keystore-test.jks", sslConfig.getProperty("h2o_ssl_jks_internal"));
            assertEquals("test123", sslConfig.getProperty("h2o_ssl_jks_password"));
            assertEquals("h2o-keystore-test.jks", sslConfig.getProperty("h2o_ssl_jts"));
            assertEquals("test123", sslConfig.getProperty("h2o_ssl_jts_password"));
        } finally {
            File keystore = new File("h2o-keystore-test.jks");
            if(keystore.exists()) {
                keystore.deleteOnExit();
            }

            File props = new File("test-ssl.properties");
            if(props.exists()) {
                props.deleteOnExit();
            }
        }
    }

    @Test
    public void testGetKeytoolClass() {
        Class<?> ktClass = SecurityUtils.getKeyToolClass();
        assertNotNull(ktClass);

        if (JavaVersionUtils.getMajorVersion() < 16) {
            assertEquals("sun.security.tools.keytool.Main", ktClass.getName());
        } else {
            assertTrue(ktClass.isAssignableFrom(ExternalKeytool.class));
        }
    }

}
